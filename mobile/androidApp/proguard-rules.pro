# R8 rules for release and qa builds. Libraries ship their own consumer rules, so this file holds
# only what they don't cover. Test any change with the qa build (docs/PHASE19.md).

# Readable crash traces once deobfuscated with mapping.txt.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
