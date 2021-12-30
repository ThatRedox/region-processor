/*
 * rs-rendernode is the worker node software of our RenderService.
 * Copyright (C) 2016 Wertarbyte <https://wertarbyte.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.lemaik.renderservice.regionprocessor.rendering;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.QueueingConsumer;
import de.lemaik.renderservice.regionprocessor.chunky.BinarySceneData;
import de.lemaik.renderservice.regionprocessor.chunky.ChunkyWrapper;
import de.lemaik.renderservice.regionprocessor.util.FileUtil;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.llbit.util.ProgressListener;
import se.llbit.util.TaskTracker;

public class AssignmentWorker implements Runnable {

  private static final Logger LOGGER = LoggerFactory.getLogger(AssignmentWorker.class);
  private static final Gson gson = new Gson();

  private final QueueingConsumer.Delivery delivery;
  private final Channel channel;
  private final Path workingDir;
  private final Path texturepacksDir;
  private final ChunkyWrapper chunky;
  private final RenderServerApiClient apiClient;

  public AssignmentWorker(QueueingConsumer.Delivery delivery, Channel channel, Path workingDir,
      Path texturepacksDir, ChunkyWrapper chunky, RenderServerApiClient apiClient) {
    this.delivery = delivery;
    this.channel = channel;
    this.workingDir = workingDir;
    this.texturepacksDir = texturepacksDir;
    this.chunky = chunky;
    this.apiClient = apiClient;
  }

  @Override
  public void run() {
    try {
      Assignment assignment = gson
          .fromJson(new String(delivery.getBody(), "UTF-8"), Assignment.class);
      LOGGER.info(String.format("New assignment for job %s", assignment.getJobId()));
      final Job job = apiClient.getJob(assignment.getJobId()).get(10, TimeUnit.MINUTES);
      if (job == null) {
        LOGGER.info("Job was deleted, skipping and removing it from the queue");
        channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
        return;
      }
      if (job.isCancelled()) {
        LOGGER.info("Job is cancelled, skipping and removing it from the queue");
        channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
        return;
      }
      LOGGER.info(String.format("%d regions", job.getRegionUrls().count()));

      final JsonObject[] sceneDescription = new JsonObject[1];
      LOGGER.info("Downloading scene files...");

      final File regionsPath = new File(workingDir.toFile(), "region");
      regionsPath.mkdirs();

      CompletableFuture.allOf(
          apiClient.getScene(job).thenAccept((scene -> {
            scene.addProperty("name", "scene");
            scene.getAsJsonObject("world").addProperty("path", "");
            sceneDescription[0] = scene;

            try (OutputStreamWriter out = new OutputStreamWriter(
                new FileOutputStream(new File(workingDir.toFile(), "scene.json")))) {
              new Gson().toJson(scene, out);
            } catch (IOException e) {
              // TODO
              e.printStackTrace();
            }
          })),
          // apiClient.downloadFoliage(job, new File(workingDir.toFile(), "scene.foliage")),
          // apiClient.downloadGrass(job, new File(workingDir.toFile(), "scene.grass")),
          CompletableFuture.allOf(
              job.getRegionUrls().map(file -> apiClient
                  .downloadFile(file.getUrl(), new File(regionsPath, file.getName())))
                  .toArray(CompletableFuture[]::new)
          )
      ).get(4, TimeUnit.HOURS); // timeout after 4 hours of downloading

      File texturepack = null;
      if (job.getTexturepack() != null) {
        texturepack = new File(texturepacksDir.toFile(), job.getTexturepack() + ".zip");
        if (!texturepack.isFile()) {
          LOGGER.info("Downloading texturepack...");
          apiClient.downloadResourcepack(job.getTexturepack(), texturepack).get(4, TimeUnit.HOURS);
        }
      }

      LOGGER.info("Generating octree...");
      BinarySceneData data = chunky
          .generateOctree(new File(workingDir.toFile(), "scene.json"), workingDir.toFile(), 0, texturepack);

      LOGGER.info("Uploading...");
      apiClient.uploadSceneData(job.getId(), data, new TaskTracker(ProgressListener.NONE)).get();

      channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
      LOGGER.info("Done");
    } catch (Exception e) {
      LOGGER.warn("An error occurred while processing a task", e);

      if (channel.isOpen()) {
        try {
          channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, true);
        } catch (IOException e1) {
          LOGGER.error("Could not nack a failed task", e);
        }
      }
    } finally {
      FileUtil.deleteDirectory(workingDir.toFile());
    }
  }
}
