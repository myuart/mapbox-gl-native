#include "custom_vector_source.hpp"

#include <mbgl/renderer/query.hpp>

// Java -> C++ conversion
#include "../android_conversion.hpp"
#include "../conversion/filter.hpp"
//#include "../conversion/geojson.hpp"

// C++ -> Java conversion
#include "../../conversion/conversion.hpp"
#include "../../conversion/collection.hpp"
#include "../../geojson/conversion/feature.hpp"
#include <mbgl/style/conversion/custom_vector_source_options.hpp>

#include <string>

namespace mbgl {
namespace android {

    // This conversion is expected not to fail because it's used only in contexts where
    // the value was originally a GeoJsonOptions object on the Java side. If it fails
    // to convert, it's a bug in our serialization or Java-side static typing.
    static style::CustomVectorSource::Options convertCustomVectorSourceOptions(jni::JNIEnv& env, jni::Object<> options, style::TileFunction fetchFn, style::TileFunction cancelFn) {
        using namespace mbgl::style::conversion;
        if (!options) {
            return style::CustomVectorSource::Options();
        }
        Error error;
        optional<style::CustomVectorSource::Options> result = convert<style::CustomVectorSource::Options>(Value(env, options), error);
        if (!result) {
            throw std::logic_error(error.message);
        }
        result->fetchTileFunction = fetchFn;
        result->cancelTileFunction = cancelFn;
        return *result;
    }

    CustomVectorSource::CustomVectorSource(jni::JNIEnv& env, jni::Object<CustomVectorSource> _obj, jni::String sourceId, jni::Object<> options)
    : Source(env, std::make_unique<mbgl::style::CustomVectorSource>(
            jni::Make<std::string>(env, sourceId),
            convertCustomVectorSourceOptions(env, options, std::bind(&CustomVectorSource::fetchTile, this, std::placeholders::_1), std::bind(&CustomVectorSource::cancelTile, this, std::placeholders::_1)))
    ), javaPeer(SeizeGenericWeakRef(env, jni::Object<CustomVectorSource>(jni::NewWeakGlobalRef(env, _obj.Get()).release()))) {

    }

    CustomVectorSource::CustomVectorSource(mbgl::style::CustomVectorSource& coreSource)
        : Source(coreSource) {
    }

    CustomVectorSource::~CustomVectorSource() = default;

    void CustomVectorSource::fetchTile (const mbgl::CanonicalTileID& tileID) {
        android::UniqueEnv _env = android::AttachEnv();
        static auto fetchTile = javaClass.GetMethod<void (jni::jint, jni::jint, jni::jint)>(*_env, "fetchTile");
        assert(javaPeer);
        javaPeer->Call(*_env, fetchTile, (int)tileID.z, (int)tileID.x, (int)tileID.y);
    };

    void CustomVectorSource::cancelTile(const mbgl::CanonicalTileID& tileID) {
        android::UniqueEnv _env = android::AttachEnv();
        static auto cancelTile = javaClass.GetMethod<void (jni::jint, jni::jint, jni::jint)>(*_env, "cancelTile");

        javaPeer->Call(*_env, cancelTile, (int)tileID.z, (int)tileID.x, (int)tileID.y);
    };

    void CustomVectorSource::setTileData(jni::JNIEnv& env, jni::jint z, jni::jint x, jni::jint y, jni::Object<geojson::FeatureCollection> jFeatures) {
        using namespace mbgl::android::geojson;

        // Convert the jni object
        auto geometry = geojson::FeatureCollection::convert(env, jFeatures);

        // Update the core source
        source.as<mbgl::style::CustomVectorSource>()->CustomVectorSource::setTileData(CanonicalTileID(z, x, y), GeoJSON(geometry));
    }

    void CustomVectorSource::invalidateTile(jni::JNIEnv&, jni::jint z, jni::jint x, jni::jint y) {
        source.as<mbgl::style::CustomVectorSource>()->CustomVectorSource::invalidateTile(CanonicalTileID(z, x, y));
    }
    void CustomVectorSource::invalidateBounds(jni::JNIEnv& env, jni::Object<LatLngBounds> jBounds) {
        auto bounds = LatLngBounds::getLatLngBounds(env, jBounds);
        source.as<mbgl::style::CustomVectorSource>()->CustomVectorSource::invalidateRegion(bounds);
    }

    jni::Array<jni::Object<geojson::Feature>> CustomVectorSource::querySourceFeatures(jni::JNIEnv& env,
                                                                        jni::Array<jni::Object<>> jfilter) {
        using namespace mbgl::android::conversion;
        using namespace mbgl::android::geojson;

        std::vector<mbgl::Feature> features;
        if (rendererFrontend) {
            features = rendererFrontend->querySourceFeatures(source.getID(), { {},  toFilter(env, jfilter) });
        }
        return *convert<jni::Array<jni::Object<Feature>>, std::vector<mbgl::Feature>>(env, features);
    }

    jni::Class<CustomVectorSource> CustomVectorSource::javaClass;

    jni::jobject* CustomVectorSource::createJavaPeer(jni::JNIEnv& env) {
        static auto constructor = CustomVectorSource::javaClass.template GetConstructor<jni::jlong>(env);
        return CustomVectorSource::javaClass.New(env, constructor, reinterpret_cast<jni::jlong>(this));
    }

    void CustomVectorSource::registerNative(jni::JNIEnv& env) {
        // Lookup the class
        CustomVectorSource::javaClass = *jni::Class<CustomVectorSource>::Find(env).NewGlobalRef(env).release();

        #define METHOD(MethodPtr, name) jni::MakeNativePeerMethod<decltype(MethodPtr), (MethodPtr)>(name)

        // Register the peer
        jni::RegisterNativePeer<CustomVectorSource>(
            env, CustomVectorSource::javaClass, "nativePtr",
            std::make_unique<CustomVectorSource, JNIEnv&, jni::Object<CustomVectorSource>, jni::String, jni::Object<>>,
            "initialize",
            "finalize",
            METHOD(&CustomVectorSource::querySourceFeatures, "querySourceFeatures"),
            METHOD(&CustomVectorSource::setTileData, "nativeSetTileData"),
            METHOD(&CustomVectorSource::invalidateTile, "nativeInvalidateTile"),
            METHOD(&CustomVectorSource::invalidateBounds, "nativeInvalidateBounds")
        );
    }

} // namespace android
} // namespace mbgl
