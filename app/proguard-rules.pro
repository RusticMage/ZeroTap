# ZeroTap ProGuard Rules
# Add project specific ProGuard rules here.

# Room
-keep class com.zerotap.data.db.entity.** { *; }

# Keep domain models for serialization
-keep class com.zerotap.domain.model.** { *; }
