package com.promptlib.shared.port;

import java.util.UUID;

/** The only way another domain may validate a category. */
public interface CategoryLookup {

    void requireActive(UUID categoryId);
}
