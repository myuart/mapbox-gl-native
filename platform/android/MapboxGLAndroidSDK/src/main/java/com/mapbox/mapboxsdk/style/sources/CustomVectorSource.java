package com.mapbox.mapboxsdk.style.sources;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.annotation.WorkerThread;

import com.mapbox.mapboxsdk.geometry.LatLngBounds;
import com.mapbox.mapboxsdk.style.layers.Filter;
import com.mapbox.services.commons.geojson.Feature;
import com.mapbox.services.commons.geojson.FeatureCollection;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

class TileID {
  public int z;
  public int x;
  public int y;
  public TileID(int _z, int _x, int _y) {
    z = _z;
    x = _x;
    y = _y;
  }
}

class TileRequest implements Runnable {
  private TileID id;
  private TileProvider provider;
  private WeakReference<CustomVectorSource> sourceRef;
  public TileRequest(TileID _id, TileProvider p, CustomVectorSource _source) {
    id = _id;
    provider = p;
    sourceRef = new WeakReference<>(_source);
  }

  public void run() {
    FeatureCollection data = provider.getFeaturesForBounds(LatLngBounds.from(id.z, id.x, id.y), id.z);
    CustomVectorSource source = sourceRef.get();
    if(source != null) {
      source.setTileData(id, data);
    }
  }
}

/**
 * Custom Vector Source, allows using FeatureCollections.
 *
 */
@UiThread
public class CustomVectorSource extends Source {
  private BlockingQueue requestQueue;
  private ThreadPoolExecutor executor;
  private TileProvider provider;

  /**
   * Internal use
   *
   * @param nativePtr - pointer to native peer
   */
  public CustomVectorSource(long nativePtr) {
    super(nativePtr);
  }

  /**
   * Create a CustomVectorSource
   *
   * @param id the source id
   */
  public CustomVectorSource(String id, TileProvider provider_) {
    requestQueue = new ArrayBlockingQueue(80);
    executor = new ThreadPoolExecutor(10, 10, 60, TimeUnit.SECONDS, requestQueue);

    provider = provider_;
    initialize(this, id, new GeoJsonOptions());
  }

  /**
   * Create a CustomVectorSource with non-default GeoJsonOptions.
   *
   * @param id      the source id
   * @param options options
   */
  public CustomVectorSource(String id, TileProvider provider_, GeoJsonOptions options) {
    provider = provider_;
    initialize(this, id, options);
  }

  public void setTileData(TileID tileId, FeatureCollection data) {
    setTileData(tileId.z, tileId.x, tileId.y, data);
  }

  /**
   * Queries the source for features.
   *
   * @param filter an optional filter statement to filter the returned Features
   * @return the features
   */
  @NonNull
  public List<Feature> querySourceFeatures(@Nullable Filter.Statement filter) {
    Feature[] features = querySourceFeatures(filter != null ? filter.toArray() : null);
    return features != null ? Arrays.asList(features) : new ArrayList<Feature>();
  }

  protected native void initialize(CustomVectorSource self, String sourceId, Object options);

  private native Feature[] querySourceFeatures(Object[] filter);

  private native void setTileData(int z, int x, int y, FeatureCollection data);

  @WorkerThread
  public void fetchTile(int z, int x, int y) {
    TileRequest request = new TileRequest(new TileID(z, x, y), provider, this);
    long active = executor.getActiveCount();
    long complete = executor.getCompletedTaskCount();
    executor.execute(request);
//    setTileData(z, x, y, data);
    System.out.println("Active=" + active);
    System.out.println("Completed=" + complete);
  }

  @WorkerThread
  public void cancelTile(int z, int x, int y) {
  }

  @Override
  protected native void finalize() throws Throwable;
}
