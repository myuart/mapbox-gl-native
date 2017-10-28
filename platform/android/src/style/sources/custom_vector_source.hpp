#pragma once

#include "source.hpp"
#include <mbgl/style/sources/custom_vector_source.hpp>
#include <mbgl/util/geojson.hpp>
#include <mbgl/tile/tile_id.hpp>
#include "../../geojson/geometry.hpp"
#include "../../geojson/feature.hpp"
#include "../../geojson/feature_collection.hpp"
#include "../../geometry/lat_lng_bounds.hpp"
#include <jni/jni.hpp>

namespace mbgl {
namespace android {

class CustomVectorSource : public Source {
public:

    static constexpr auto Name() { return "com/mapbox/mapboxsdk/style/sources/CustomVectorSource"; };

    static jni::Class<CustomVectorSource> javaClass;

    static void registerNative(jni::JNIEnv&);

    CustomVectorSource(jni::JNIEnv&,
                       jni::Object<CustomVectorSource>,
                       jni::String,
                       jni::Object<>);

    CustomVectorSource(mbgl::style::CustomVectorSource&);

    ~CustomVectorSource();

    void fetchTile(const mbgl::CanonicalTileID& tileID);
    void cancelTile(const mbgl::CanonicalTileID& tileID);
    void setTileData(jni::JNIEnv& env, jni::jint z, jni::jint x, jni::jint y, jni::Object<geojson::FeatureCollection> jf);

    void invalidateTile(jni::JNIEnv& env, jni::jint z, jni::jint x, jni::jint y);
    void invalidateBounds(jni::JNIEnv& env, jni::Object<LatLngBounds> bounds);

    jni::Array<jni::Object<geojson::Feature>> querySourceFeatures(jni::JNIEnv&,
                                                                  jni::Array<jni::Object<>> );

    jni::jobject* createJavaPeer(jni::JNIEnv&);

    GenericUniqueWeakObject<CustomVectorSource> javaPeer;
}; // class CustomVectorSource

} // namespace android
} // namespace mbgl
