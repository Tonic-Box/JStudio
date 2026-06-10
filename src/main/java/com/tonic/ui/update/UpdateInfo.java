package com.tonic.ui.update;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * A resolved GitHub release: its tag, parsed numeric version, release page, and download URLs for the
 * {@code JStudio.jar} asset and its optional {@code JStudio.jar.sha256} checksum.
 */
@Getter
@RequiredArgsConstructor
public final class UpdateInfo {

    private final String tag;
    private final int version;
    private final String releaseUrl;
    private final String jarUrl;
    private final String sha256Url;
}
