package com.tonic.ui.update;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** A GitHub release: its tag, numeric version, page, and download URLs of the JStudio.jar asset and its optional checksum. */
@Getter
@RequiredArgsConstructor
public final class UpdateInfo
{

    private final String tag;
    private final int version;
    private final String releaseUrl;
    private final String jarUrl;
    private final String sha256Url;
}
