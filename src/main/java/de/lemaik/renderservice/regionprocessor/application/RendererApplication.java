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

package de.lemaik.renderservice.regionprocessor.application;

import de.lemaik.renderservice.regionprocessor.chunky.BinarySceneData;
import de.lemaik.renderservice.regionprocessor.chunky.ChunkyWrapper;
import de.lemaik.renderservice.regionprocessor.chunky.ChunkyWrapperFactory;
import de.lemaik.renderservice.regionprocessor.chunky.EmbeddedChunkyWrapper;
import de.lemaik.renderservice.regionprocessor.rendering.RenderServerApiClient;
import de.lemaik.renderservice.regionprocessor.rendering.RenderServiceInfo;
import de.lemaik.renderservice.regionprocessor.rendering.RenderWorker;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class RendererApplication {

  private static final int VERSION = 2;
  private static final Logger LOGGER = LogManager.getLogger(RendererApplication.class);

  private final RenderServerApiClient api;
  private final RendererSettings settings;
  private Path jobDirectory;
  private ChunkyWrapperFactory chunkyWrapperFactory;

  private RenderWorker worker;
  private UUID id = UUID.randomUUID();

  public RendererApplication(RendererSettings settings) {
    this.settings = settings;
    api = new RenderServerApiClient(
        settings.getMasterApiUrl(),
        settings.getCacheDirectory()
            .orElse(Paths.get(System.getProperty("user.dir"), "rs_cache").toFile()),
        settings.getMaxCacheSize().orElse(512L)
    );
  }

  public void start() {
    RenderServiceInfo rsInfo;
    try {
      rsInfo = api.getInfo().get();
    } catch (Exception e) {
      LOGGER.error("Could not fetch render service info", e);
      System.exit(-1);
      return;
    }

    if (rsInfo.getVersion() > VERSION) {
      LOGGER.error("Update required. The minimum required version is " + rsInfo.getVersion()
          + ", your version is " + VERSION + ".");
      System.exit(-42);
      return;
    }

    if (getSettings().getJobPath().isPresent()) {
      jobDirectory = getSettings().getJobPath().get().toPath();
    } else {
      jobDirectory = Paths.get(System.getProperty("user.dir"), "rs_jobs");
      LOGGER.warn("No job path specified, using " + jobDirectory.toString());
    }
    jobDirectory.toFile().mkdirs();

    chunkyWrapperFactory = EmbeddedChunkyWrapper::new;

    worker = new RenderWorker(rsInfo.getRabbitMq(), getSettings().getName().orElse(null),
        jobDirectory, chunkyWrapperFactory, api);
    worker.start();
  }

  public UUID getId() {
    return id;
  }

  public RendererSettings getSettings() {
    return settings;
  }

  public void stop() {
    try {
      LOGGER.info("Waiting for worker to stop...");
      worker.interrupt();
      worker.join();
      LOGGER.info("Worker stopped");
    } catch (InterruptedException e) {
      LOGGER.error("Could not gracefully stop the renderer");
    }
  }

  protected abstract void onUpdateAvailable();
}
