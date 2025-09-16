#!/bin/bash

# Fix all controller exceptionally methods to use handle instead
find src/main/java/com/java_template/application/controller -name "*.java" -exec sed -i 's/\.exceptionally(throwable -> {/.handle((result, throwable) -> {\n                if (throwable != null) {/g' {} \;

find src/main/java/com/java_template/application/controller -name "*.java" -exec sed -i 's/return ResponseEntity\.status(HttpStatus\.INTERNAL_SERVER_ERROR)\.build();/return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();\n                }\n                return result;/g' {} \;

find src/main/java/com/java_template/application/controller -name "*.java" -exec sed -i 's/return ResponseEntity\.status(HttpStatus\.NOT_FOUND)\.build();/return ResponseEntity.status(HttpStatus.NOT_FOUND).build();\n                }\n                return result;/g' {} \;
