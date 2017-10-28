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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Custom Vector Source, allows using FeatureCollections.
 *
 */
@UiThread
public class CustomVectorSource extends Source {
  private BlockingQueue requestQueue;
  private ThreadPoolExecutor executor;
  private GeometryTileProvider provider;
  private Map<TileID, AtomicBoolean> cancelledTileRequests;

  /**
   * Create a CustomVectorSource
   *
   * @param id the source id
   */
  public CustomVectorSource(String id, GeometryTileProvider provider_) {
    requestQueue = new ArrayBlockingQueue(80);
    executor = new ThreadPoolExecutor(2, 2, 60, TimeUnit.SECONDS, requestQueue);
    cancelledTileRequests = new HashMap<>();
    provider = provider_;
    initialize(this, id, new GeoJsonOptions());
  }

  /**
   * Create a CustomVectorSource with non-default GeoJsonOptions.
   *
   * @param id      the source id
   * @param options options
   */
  public CustomVectorSource(String id, GeometryTileProvider provider_, GeoJsonOptions options) {
    provider = provider_;
    initialize(this, id, options);
  }

  /**
   *  Invalidate previously provided features within a given bounds at all zoom levels.
   *  Invoking this method will result in new requests to `GeometryTileProvider` for regions
   *  that contain, include, or intersect with the provided bounds.
   * @param bounds The region in which features should be invalidated at all zoom levels
   */
  public void invalidateRegion(LatLngBounds bounds) {
    nativeInvalidateBounds(bounds);
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

  private native void nativeSetTileData(int z, int x, int y, FeatureCollection data);
  private native void nativeInvalidateTile(int z, int x, int y);
  private native void nativeInvalidateBounds(LatLngBounds bounds);

  @Override
  protected native void finalize() throws Throwable;

  private void setTileData(TileID tileId, FeatureCollection data) {
    cancelledTileRequests.remove(tileId);
    nativeSetTileData(tileId.z, tileId.x, tileId.y, data);
  }

  @WorkerThread
  private void fetchTile(int z, int x, int y) {
    AtomicBoolean cancelFlag = new AtomicBoolean(false);
    TileID tileID = new TileID(z, x, y);
    cancelledTileRequests.put(tileID, cancelFlag);
    GeometryTileRequest request = new GeometryTileRequest(tileID, provider, this, cancelFlag);
    executor.execute(request);
  }

  @WorkerThread
  private void cancelTile(int z, int x, int y) {
    AtomicBoolean cancelFlag = cancelledTileRequests.get(new TileID(z,x,y));
    if (cancelFlag != null) {
      cancelFlag.compareAndSet(false, true);
    }
  }

  class TileID {
    public int z;
    public int x;
    public int y;
    public TileID(int _z, int _x, int _y) {
      z = _z;
      x = _x;
      y = _y;
    }

    public int hashCode() {
      return Arrays.hashCode(new int[]{z, x, y});
    }

    public boolean equals(Object object) {
      if (object == this) {
        return true;
      }

      if (object == null || getClass() != object.getClass()) {
        return false;
      }

      if(object instanceof TileID) {
        TileID other = (TileID)object;
        return this.z == other.z && this.x == other.x && this.y == other.y;
      }
      return false;
    }
  }

  class GeometryTileRequest implements Runnable {
    private TileID id;
    private GeometryTileProvider provider;
    private WeakReference<CustomVectorSource> sourceRef;
    private AtomicBoolean cancelled;
    public GeometryTileRequest(TileID _id, GeometryTileProvider p, CustomVectorSource _source, AtomicBoolean _cancelled) {
      id = _id;
      provider = p;
      sourceRef = new WeakReference<>(_source);
      cancelled = _cancelled;
    }

    public void run() {
      if (isCancelled()) {
        return;
      }

      FeatureCollection data = provider.getFeaturesForBounds(LatLngBounds.from(id.z, id.x, id.y), id.z);
      CustomVectorSource source = sourceRef.get();
      if(!data.getFeatures().isEmpty() && source != null && !isCancelled())  {
        source.setTileData(id, data);
      }
    }

    private Boolean isCancelled() {
      return cancelled.get();
    }
  }
}
