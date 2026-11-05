# Wear module Proguard rules
# Add any rules here to preserve classes needed at runtime

# Health Services client serializes its IPC messages with protobuf-lite, which
# looks up message fields by name via reflection (e.g. "packageName_").
# Neither health-services-client nor protobuf-javalite ship consumer rules, so
# without this R8 renames the fields and every Health Services call fails with
# "Field xxx_ for ... not found", leaving the watch stuck at "--" BPM.
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}
