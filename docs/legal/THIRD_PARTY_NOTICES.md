# Smooth Media — Third-Party Notices

This file records third-party components used by Smooth Media. It is intended
to be shipped with the application and kept in sync with the release build.

## Kyant0/taglib Android wrapper

- Component: `io.github.kyant0:taglib`
- Version: `1.0.5`
- Repository: https://github.com/Kyant0/taglib
- License: Apache License 2.0
- License text: https://raw.githubusercontent.com/Kyant0/taglib/main/LICENSE

Smooth Media uses the public Android wrapper API for reading and writing MP3
ID3 metadata and FLAC metadata/pictures. No source code, artwork, or UI assets
were copied from the repository.

The Apache License 2.0 notice and attribution must remain available in every
distributed build.

## Native TagLib

The Kyant0/taglib Android artifact bundles native TagLib functionality. The
native upstream project is separately licensed under LGPL and MPL terms.

- Project: https://taglib.org/
- Upstream license information: https://taglib.org/license/
- Kyant0 release notes identifying the native TagLib update: https://github.com/Kyant0/taglib/releases
- Resolved native source snapshot in `1.0.5`: `3c917caf52b6d14bfb548d4dc27d8601815edbe2`
- Upstream license files verified at that snapshot: `COPYING.LGPL`, `COPYING.MPL`

Smooth Media does not modify the native TagLib source. Before each release,
the resolved native snapshot and its license/notice files must be checked and
included here or in the packaged third-party notices. The resolved `1.0.5` AAR
does not contain standalone license files.

## Reference projects — no code or assets copied

The following projects were reviewed as behavioral or UX references only. No
source code, icons, screenshots, strings, or other assets from them are part
of Smooth Media:

- Metadator — AGPL-3.0: https://github.com/BobbyESP/Metadator
- MP3-Cover-Editor — https://github.com/PauLopNun/MP3-Cover-Editor
- Tagger — WTFPL: https://github.com/mvxGREEN/Tagger

If code or assets are ever copied from one of these projects, this notice must
be revised and the corresponding license obligations reviewed before merging.

## User-provided media

Music files, embedded artwork, and metadata selected by the user remain user
content. Release artifacts and automated tests must not include copyrighted
commercial music or artwork unless the project has the necessary rights.

## Release checklist

- Verify the resolved dependency graph and license texts for the exact release.
- Verify the native TagLib version and notices inside the resolved AAR.
- Ship this notice with the APK or release bundle.
- Keep Smooth Media's own project license separate; this file does not grant
  a license to Smooth Media's source code.
