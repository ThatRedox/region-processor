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
import de.lemaik.renderservice.regionprocessor.chunky.BinarySceneData;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import okhttp3.Cache;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;
import okio.Okio;
import se.llbit.util.TaskTracker;
import se.llbit.util.TaskTracker.Task;

public class RenderServerApiClient {

  private static final Gson gson = new Gson();
  private final String baseUrl;
  private final OkHttpClient client;

  public RenderServerApiClient(String baseUrl, String apiKey, File cacheDirectory,
      long maxCacheSize) {
    this.baseUrl = baseUrl;
    client = new OkHttpClient.Builder()
        .cache(new Cache(cacheDirectory, maxCacheSize * 1024 * 1024))
        .addInterceptor(chain -> chain.proceed(
            chain.request().newBuilder()
                .header("X-Api-Key", apiKey)
                .build()))
        .build();
  }

  public CompletableFuture<RenderServiceInfo> getInfo() {
    CompletableFuture<RenderServiceInfo> result = new CompletableFuture<>();

    client.newCall(new Request.Builder()
        .url(baseUrl + "/info").get().build())
        .enqueue(new Callback() {
          @Override
          public void onFailure(Call call, IOException e) {
            result.completeExceptionally(e);
          }

          @Override
          public void onResponse(Call call, Response response) {
            if (response.code() == 200) {
              try (InputStreamReader reader = new InputStreamReader(response.body().byteStream())) {
                result.complete(gson.fromJson(reader, RenderServiceInfo.class));
              } catch (IOException e) {
                result.completeExceptionally(e);
              }
            } else {
              result.completeExceptionally(
                  new IOException("The render service info could not be downloaded"));
            }
          }
        });

    return result;
  }

  public CompletableFuture<Job> getJob(String jobId) {
    CompletableFuture<Job> result = new CompletableFuture<>();

    client.newCall(new Request.Builder()
        .url(baseUrl + "/jobs/" + jobId).get().build())
        .enqueue(new Callback() {
          @Override
          public void onFailure(Call call, IOException e) {
            result.completeExceptionally(e);
          }

          @Override
          public void onResponse(Call call, Response response) {
            if (response.code() == 200) {
              try (InputStreamReader reader = new InputStreamReader(response.body().byteStream())) {
                result.complete(gson.fromJson(reader, Job.class));
              } catch (IOException e) {
                result.completeExceptionally(e);
              }
            } else {
              try {
                if (response.code() == 404 && response.body().string().contains("Job not found")) {
                  result.complete(null);
                } else {
                  result.completeExceptionally(new IOException("The job could not be downloaded"));
                }
              } catch (IOException e) {
                result.completeExceptionally(new IOException("The job could not be downloaded", e));
              }
            }
          }
        });

    return result;
  }

  public CompletableFuture<JsonObject> getScene(Job job) {
    CompletableFuture<JsonObject> result = new CompletableFuture<>();

    client.newCall(new Request.Builder()
        .url(baseUrl + job.getSceneUrl()).get().build())
        .enqueue(new Callback() {
          @Override
          public void onFailure(Call call, IOException e) {
            result.completeExceptionally(e);
          }

          @Override
          public void onResponse(Call call, Response response) {
            if (response.code() == 200) {
              try (InputStreamReader reader = new InputStreamReader(response.body().byteStream())) {
                result.complete(gson.fromJson(reader, JsonObject.class));
              } catch (IOException e) {
                result.completeExceptionally(e);
              }
            } else {
              result.completeExceptionally(new IOException("The scene could not be downloaded"));
            }
          }
        });

    return result;
  }

  public CompletableFuture downloadFoliage(Job job, File file) {
    return downloadFile(job.getFoliageUrl().get(), file);
  }

  public CompletableFuture downloadGrass(Job job, File file) {
    return downloadFile(job.getGrassUrl().get(), file);
  }

  public CompletableFuture downloadOctree(Job job, File file) {
    return downloadFile(job.getOctreeUrl(), file);
  }

  public CompletableFuture downloadEmittergrid(Job job, File file) {
    Optional<String> url = job.getEmittergridUrl();
    return url.map(s -> downloadFile(s, file))
        .orElseGet(() -> CompletableFuture.completedFuture(null));
  }

  public CompletableFuture<File> downloadSkymapTo(String url, Path targetDir) {
    CompletableFuture<File> result = new CompletableFuture<>();

    client.newCall(new Request.Builder()
        .url(baseUrl + url).get().build())
        .enqueue(new Callback() {
          @Override
          public void onFailure(Call call, IOException e) {
            result.completeExceptionally(e);
          }

          @Override
          public void onResponse(Call call, Response response) {
            if (response.code() == 200) {
              File file = new File(targetDir.toFile(), response.header("X-Filename"));

              try (BufferedSink sink = Okio.buffer(Okio.sink(file))) {
                sink.writeAll(response.body().source());
                result.complete(file);
              } catch (IOException e) {
                result.completeExceptionally(e);
              }
            } else {
              result.completeExceptionally(new IOException("Download of " + url + " failed"));
            }
          }
        });

    return result;
  }

  public CompletableFuture<File> downloadFile(String url, File file) {
    CompletableFuture<File> result = new CompletableFuture<>();

    client.newCall(new Request.Builder()
        .url(baseUrl + url).get().build())
        .enqueue(new Callback() {
          @Override
          public void onFailure(Call call, IOException e) {
            result.completeExceptionally(e);
          }

          @Override
          public void onResponse(Call call, Response response) {
            if (response.code() == 200) {
              try (BufferedSink sink = Okio.buffer(Okio.sink(file))) {
                sink.writeAll(response.body().source());
                result.complete(file);
              } catch (IOException e) {
                result.completeExceptionally(e);
              }
            } else {
              result.completeExceptionally(new IOException("Download of " + url + " failed"));
            }
            response.close();
          }
        });

    return result;
  }

  public CompletableFuture<Void> uploadSceneData(String id, BinarySceneData data,
      TaskTracker taskTracker) {
    CompletableFuture<Void> result = new CompletableFuture<>();

    MultipartBody.Builder multipartBuilder = new MultipartBody.Builder()
        .setType(MediaType.parse("multipart/form-data"))
        .addFormDataPart("octree", "scene.octree2",
            byteBody(data.getOctree(), () -> taskTracker.task("Upload octree...")));
    if (data.getEmittergrid() != null) {
      multipartBuilder = multipartBuilder.addFormDataPart("emittergrid", "scene.emittergrid",
          byteBody(data.getEmittergrid(), () -> taskTracker.task("Upload emittergrid...")));
    }

    client.newCall(new Request.Builder()
        .url(baseUrl + "/jobs/" + id + "/files")
        .post(multipartBuilder.build())
        .build())
        .enqueue(new Callback() {
          @Override
          public void onFailure(Call call, IOException e) {
            result.completeExceptionally(e);
          }

          @Override
          public void onResponse(Call call, Response response) throws IOException {
            if (response.code() == 204) {
              result.complete(null);
            } else {
              result.completeExceptionally(
                  new IOException("The render job could not be updated " + response.message()));
            }
            response.close();
          }
        });

    return result;
  }

  private static RequestBody byteBody(final byte[] content,
      Supplier<Task> taskCreator) {
    TaskTracker.Task task = taskCreator.get();
    return new RequestBody() {
      @Override
      public MediaType contentType() {
        return MediaType.parse("application/octet-stream");
      }

      @Override
      public long contentLength() {
        return content.length;
      }

      @Override
      public void writeTo(BufferedSink bufferedSink) throws IOException {
        bufferedSink.write(content);
        task.close();
      }
    };
  }
}
