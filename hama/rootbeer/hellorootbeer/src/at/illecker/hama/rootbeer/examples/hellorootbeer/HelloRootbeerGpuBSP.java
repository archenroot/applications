/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package at.illecker.hama.rootbeer.examples.hellorootbeer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import junit.framework.Assert;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.DoubleWritable;
import org.apache.hadoop.io.IOUtils;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hama.HamaConfiguration;
import org.apache.hama.bsp.BSP;
import org.apache.hama.bsp.BSPJob;
import org.apache.hama.bsp.BSPPeer;
import org.apache.hama.bsp.FileOutputFormat;
import org.apache.hama.bsp.NullInputFormat;
import org.apache.hama.bsp.TextOutputFormat;
import org.apache.hama.bsp.sync.SyncException;
import org.trifort.rootbeer.runtime.Context;
import org.trifort.rootbeer.runtime.Kernel;
import org.trifort.rootbeer.runtime.Rootbeer;
import org.trifort.rootbeer.runtime.StatsRow;
import org.trifort.rootbeer.runtime.util.Stopwatch;

public class HelloRootbeerGpuBSP extends
    BSP<NullWritable, NullWritable, Text, DoubleWritable, DoubleWritable> {
  private static final Log LOG = LogFactory.getLog(HelloRootbeerGpuBSP.class);
  private static final Path TMP_OUTPUT = new Path(
      "output/hama/rootbeer/examples/hellorootbeer/GPU-"
          + System.currentTimeMillis());
  private static final long kernelCount = 100;
  private static final long iterations = 10000;

  private String m_masterTask;
  private int m_kernelCount;
  private long m_iterations;

  @Override
  public void setup(
      BSPPeer<NullWritable, NullWritable, Text, DoubleWritable, DoubleWritable> peer)
      throws IOException {

    this.m_kernelCount = Integer.parseInt(peer.getConfiguration().get(
        "hellorootbeer.kernelCount"));
    this.m_iterations = Long.parseLong(peer.getConfiguration().get(
        "hellorootbeer.iterations"));
    // Choose one as a master
    this.m_masterTask = peer.getPeerName(peer.getNumPeers() / 2);

  }

  @Override
  public void bsp(
      BSPPeer<NullWritable, NullWritable, Text, DoubleWritable, DoubleWritable> peer)
      throws IOException, SyncException, InterruptedException {

    // Generate GPU Kernels
    List<Kernel> kernels = new ArrayList<Kernel>();
    for (int i = 0; i < m_kernelCount; i++) {
      kernels.add(new HelloRootbeerKernel(m_iterations));
    }

    // Run GPU Kernels
    Rootbeer rootbeer = new Rootbeer();
    Context context = rootbeer.createDefaultContext();
    Stopwatch watch = new Stopwatch();
    watch.start();
    rootbeer.run(kernels, context);
    watch.stop();

    // Write log to dfs
    BSPJob job = new BSPJob((HamaConfiguration) peer.getConfiguration());
    FileSystem fs = FileSystem.get(peer.getConfiguration());
    FSDataOutputStream outStream = fs.create(new Path(FileOutputFormat
        .getOutputPath(job), peer.getTaskId() + ".log"));

    outStream.writeChars("KernelCount: " + m_kernelCount + "\n");
    outStream.writeChars("Iterations: " + m_iterations + "\n");
    outStream.writeChars("GPU time: " + watch.elapsedTimeMillis() + " ms\n");
    List<StatsRow> stats = context.getStats();
    for (StatsRow row : stats) {
      outStream.writeChars("  StatsRow:\n");
      outStream.writeChars("    serial time: " + row.getSerializationTime()
          + "\n");
      outStream.writeChars("    exec time: " + row.getExecutionTime() + "\n");
      outStream.writeChars("    deserial time: " + row.getDeserializationTime()
          + "\n");
      outStream.writeChars("    num blocks: " + row.getNumBlocks() + "\n");
      outStream.writeChars("    num threads: " + row.getNumThreads() + "\n");
    }
    outStream.close();

    // Send result to MasterTask
    for (int i = 0; i < m_kernelCount; i++) {
      HelloRootbeerKernel kernel = (HelloRootbeerKernel) kernels.get(i);
      peer.send(m_masterTask, new DoubleWritable(kernel.m_result));
    }
    peer.sync();
  }

  @Override
  public void cleanup(
      BSPPeer<NullWritable, NullWritable, Text, DoubleWritable, DoubleWritable> peer)
      throws IOException {

    if (peer.getPeerName().equals(m_masterTask)) {

      double sum = 0.0;

      DoubleWritable received;
      while ((received = peer.getCurrentMessage()) != null) {
        sum += received.get();
      }

      double expectedResult = peer.getNumPeers() * m_kernelCount * m_iterations;
      Assert.assertEquals(expectedResult, sum);

      peer.write(new Text("Result of "
          + (peer.getNumPeers() * m_kernelCount * m_iterations)
          + " calculations is"), new DoubleWritable(sum));
    }
  }

  static void printOutput(BSPJob job) throws IOException {
    FileSystem fs = FileSystem.get(job.getConfiguration());
    FileStatus[] files = fs.listStatus(FileOutputFormat.getOutputPath(job));
    for (int i = 0; i < files.length; i++) {
      if (files[i].getLen() > 0) {
        System.out.println("File " + files[i].getPath());
        FSDataInputStream in = fs.open(files[i].getPath());
        IOUtils.copyBytes(in, System.out, job.getConfiguration(), false);
        in.close();
      }
    }
    // fs.delete(FileOutputFormat.getOutputPath(job), true);
  }

  public static void main(String[] args) throws InterruptedException,
      IOException, ClassNotFoundException {
    // BSP job configuration
    HamaConfiguration conf = new HamaConfiguration();

    BSPJob job = new BSPJob(conf);
    // Set the job name
    job.setJobName("HelloRootbeer GPU Example");
    // set the BSP class which shall be executed
    job.setBspClass(HelloRootbeerGpuBSP.class);
    // help Hama to locale the jar to be distributed
    job.setJarByClass(HelloRootbeerGpuBSP.class);

    job.setInputFormat(NullInputFormat.class);

    job.setOutputKeyClass(Text.class);
    job.setOutputValueClass(DoubleWritable.class);
    job.setOutputFormat(TextOutputFormat.class);
    FileOutputFormat.setOutputPath(job, TMP_OUTPUT);

    job.set("bsp.child.java.opts", "-Xmx4G");

    if (args.length > 0) {
      if (args.length == 3) {
        job.setNumBspTask(Integer.parseInt(args[0]));
        job.set("hellorootbeer.kernelCount", args[1]);
        job.set("hellorootbeer.iterations", args[2]);
      } else {
        System.out.println("Wrong argument size!");
        System.out.println("    Argument1=numBspTask");
        System.out.println("    Argument2=kernelCount");
        System.out.println("    Argument3=iterations");
        return;
      }
    } else {
      job.setNumBspTask(1); // one GPU task only
      job.set("hellorootbeer.kernelCount", "" + HelloRootbeerGpuBSP.kernelCount);
      job.set("hellorootbeer.iterations", "" + HelloRootbeerGpuBSP.iterations);
    }
    LOG.info("NumBspTask: " + job.getNumBspTask());
    LOG.info("KernelCount: " + job.get("hellorootbeer.kernelCount"));
    LOG.info("Iterations: " + job.get("hellorootbeer.iterations"));

    long startTime = System.currentTimeMillis();
    if (job.waitForCompletion(true)) {
      printOutput(job);
      System.out.println("Job Finished in "
          + (System.currentTimeMillis() - startTime) / 1000.0 + " seconds");
    }
  }
}
