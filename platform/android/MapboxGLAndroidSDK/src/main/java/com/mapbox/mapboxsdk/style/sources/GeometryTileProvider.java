package com.mapbox.mapboxsdk.style.sources;

import android.support.annotation.WorkerThread;

import com.mapbox.mapboxsdk.geometry.LatLngBounds;
import com.mapbox.services.commons.geojson.FeatureCollection;
import com.mapbox.services.commons.geojson.GeoJSON;


public interface GeometryTileProvider {
  @WorkerThread
  FeatureCollection getFeaturesForBounds(LatLngBounds bounds, int zoomLevel);
}