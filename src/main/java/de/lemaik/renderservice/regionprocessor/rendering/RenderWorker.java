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

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.QueueingConsumer;
import de.lemaik.renderservice.regionprocessor.Main;
import de.lemaik.renderservice.regionprocessor.chunky.ChunkyWrapperFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

/**
 * A renderer worker thread.
 */
public class RenderWorker extends Thread {

  private static final Logger LOGGER = LoggerFactory.getLogger(RenderWorker.class);
  private static final String QUEUE_NAME = "rs_prepare_241";
  private final ExecutorService executorService;
  private final Path jobDirectory;
  private final Path texturepacksDirectory;
  private final ChunkyWrapperFactory chunkyFactory;
  private final int MAX_RESTART_DELAY_SECONDS = 15 * 60; // 15 minutes
  private final RenderServerApiClient apiClient;
  private int nextRestartDelaySeconds = 1;
  private ConnectionFactory factory;
  private Connection conn;
  private Channel channel;

  public RenderWorker(String uri, String name, Path jobDirectory, Path texturepacksDirectory,
      ChunkyWrapperFactory chunkyFactory,
      RenderServerApiClient apiClient) {
    executorService = Executors.newFixedThreadPool(1);
    this.jobDirectory = jobDirectory;
    this.texturepacksDirectory = texturepacksDirectory;
    this.chunkyFactory = chunkyFactory;
    this.apiClient = apiClient;
    factory = new ConnectionFactory();
    try {
      factory.setUri(uri);
    } catch (URISyntaxException | NoSuchAlgorithmException | KeyManagementException e) {
      throw new IllegalArgumentException("Invalid RabbitMQ URI", e);
    }

    Map<String, Object> connectionProps = factory.getClientProperties();
    if (name != null) {
      connectionProps.put("x-rs-name", name);
    }
    connectionProps.put("x-version", Main.VERSION);
    factory.setClientProperties(connectionProps);
  }

  @Override
  public void run() {
    while (!interrupted()) {
      LOGGER.info("Connecting");
      try {
        connect();
        LOGGER.info("Connected");
        nextRestartDelaySeconds = 1;

        QueueingConsumer consumer = new QueueingConsumer(channel);
        channel.basicQos(1, false);
        channel.basicConsume(QUEUE_NAME, false, consumer);

        while (!interrupted() && channel.isOpen()) {
          try {
            Path assignmentPath = jobDirectory.resolve(UUID.randomUUID().toString());
            assignmentPath.toFile().mkdir();
            executorService.submit(
                new AssignmentWorker(consumer.nextDelivery(), channel, assignmentPath,
                    texturepacksDirectory, chunkyFactory.getChunkyInstance(), apiClient));
          } catch (InterruptedException e) {
            LOGGER.info("Worker loop interrupted", e);
            break;
          }
        }
      } catch (Exception e) {
        LOGGER.error("An error occurred in the worker loop", e);
      }

      if (conn != null && conn.isOpen()) {
        try {
          conn.close(5000);
        } catch (IOException e) {
          LOGGER.error("An error occurred while shutting down", e);
        }
      }

      if (!interrupted()) {
        LOGGER.info("Waiting " + nextRestartDelaySeconds + " seconds before restarting...");
        try {
          Thread.sleep(nextRestartDelaySeconds * 1000);
          nextRestartDelaySeconds = Math
              .min(MAX_RESTART_DELAY_SECONDS, nextRestartDelaySeconds * 2);
        } catch (InterruptedException e) {
          LOGGER.warn("Interrupted while sleeping", e);
          return;
        }
      } else {
        return;
      }
    }
  }

  private void connect() throws IOException {
    try {
      conn = factory.newConnection();
      channel = conn.createChannel();
    } catch (TimeoutException e) {
      throw new IOException("Timeout while connecting to RabbitMQ", e);
    }
  }
}
