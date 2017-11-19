package com.mulesoft.maven.sso

trait FileHelper {
    File getTestResources() {
        getFile('src', 'test', 'resources')
    }

    File getFile(String... parts) {
        getFile(new File(parts[0]),
                *parts[1..-1].toArray())
    }

    File getFile(File file,
                 String... parts) {
        parts.inject(file) { File existing, String part ->
            new File(existing, part)
        }
    }
}
