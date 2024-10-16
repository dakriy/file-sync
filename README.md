# Audio File Sync

A flexible and extensible audio file automation program written in Kotlin/JVM.

## Features

- **FTP** Supports FTP sources.
- **NextCloud** Supports NextCloud sources.
- **LibreTime** Supports uploading tracks to LibreTime.
- **Extensible** New sources and outputs can be added via configuration.
- **Parallel** Supports downloading many files at the same time.
- **Audio Conversion** Converts audio types via FFMPEG.
- **Audio Tagging** Supports tagging audio based on input files.
- **Dates** Supports date parsing and formatting

## Table of Contents

* [Audio File Sync](#audio-file-sync)
  * [Features](#features)
  * [Table of Contents](#table-of-contents)
  * [Use](#use)
  * [Configuration](#configuration)
    * [Sources](#sources)
      * [Example](#example)
    * [Programs](#programs)
      * [ParseSpec](#parsespec)
      * [SourceImplSpec](#sourceimplspec)
      * [Output](#output)
      * [Example](#example-1)
    * [Output](#output-1)
      * [OutputConnectorSpec](#outputconnectorspec)
      * [Example](#example-2)
  * [Supported Outputs](#supported-outputs)
    * [LibreTime](#libretime)
  * [Supported Audio Tags](#supported-audio-tags)

## Use

To run, download the latest release from GitHub releases, unzip the zip file, and run
`bin/file-sync --help`. Or build and use the docker container.

## Configuration

By default the program looks for a `config.yaml` file in the current directory. You can give it a
configuration file by specifying `-f` and the path to the configuration file.

The program works by downloading **Programs** from **Sources**. It then applies
transformations defined at the program level, and then sends them to the configured **Output**.
Downloaded audio files in a **Program** are called **Items**.

Every configuration file has the following basic format:

```yaml
fileSync:
  output: # Output options
  sources:
    # Array of sources
    - name: source1
  programs:
    # Array of programs
    - name: program1
  stopOnFailure: false # Optional, default is false. Stops on first failure.
```

### Sources

A source has the following options:

| Option                 | Type       | Default  | Description                                                                                                                                 |
|------------------------|------------|----------|---------------------------------------------------------------------------------------------------------------------------------------------|
| name                   | string     | Required | The name of the source.                                                                                                                     |
| type                   | SourceType | Required | Can be `Empty`, `FTP`, `NextCloud`, or `Custom`.                                                                                            |
| url                    | string     | null     | The url of the source. Used for `FTP` and `NextCloud`.                                                                                      |
| username               | string     | null     | The username to use when connecting to the source.                                                                                          |
| password               | string     | null     | The password to use when connecting to the source.                                                                                          |
| port                   | int        | null     | The port to use if it is not the default port for the source type.                                                                          |
| class                  | string     | null     | Required when type is `Custom`. The java class name to use for the source. Must extend the `com.persignum.filesync.domain.Source` interface |
| maxConcurrentDownloads | int        | 10       | The maximum number of concurrent downloads allowed from this source at a time.                                                              |

#### Example

```yaml
fileSync:
  sources:
    - name: government ftp server
      type: FTP
      url: ftp.example.com
      username: steve
      password: secure!password
      port: 2783
      maxConcurrentDownloads: 1
```

### Programs

A program has the following options

| Option | Type           | Default  | Description                                              |
|--------|----------------|----------|----------------------------------------------------------|
| name   | string         | Required | The name of the program.                                 |
| parse  | ParseSpec      | null     | The parse configuration                                  |
| output | Output         | null     | The output options for program files.                    |
| source | SourceImplSpec | nul      | Program specific configuration for pulling from a source |

#### ParseSpec

The parse spec configures and matches files from the source. It also allows for capture groups to
pull out information from file names for use in tags and the output file name. Only the Java regex
flavor is understood as the java engine is used. See
the [Java Pattern Class Documentation](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/regex/Pattern.html)
for specifics.

| Option      | Type                | Default  | Description                                                                                                                                                                                                                                                                                                   |
|-------------|---------------------|----------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| regex       | string              | Required | The regex pattern to match. Any capture groups can be referenced in the `output` section of program configuration.                                                                                                                                                                                            |
| dates       | Map<String, String> | []       | The key in the map references a capture group from the `regex` to parse as a date. The value is the format to parse from. See the [Date Time Formatter](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/format/DateTimeFormatter.html) documentation for all of the format specifiers. |
| strict      | boolean             | false    | When strict is enabled, any items in the program that do not match the regex will cause an error rather than a warning message.                                                                                                                                                                               |
| entireMatch | boolean             | false    | When entireMatch is enabled, partial regex matches are ignored. Otherwise, partial regex matches are used.                                                                                                                                                                                                    |

#### SourceImplSpec

The `SourceImplSpec` defines program specific options about where and how to pull the program from
the source since multiple programs can share a source.

| Option     | Type         | Default  | Description                                                                                                                                                                            |
|------------|--------------|----------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| name       | string       | Required | The name of a defined source to pull the program from.                                                                                                                                 |
| depth      | Int          | 1        | If there are folders underneath the defined path, the depth will allow you to set how far you want to traverse the directory structure to find items. Not supported for `FTP` sources. |
| path       | string       | null     | The path on the source where the program lives.                                                                                                                                        |
| extensions | List<String> | null     | Allowed list of file extensions. No file extension whitelist is applied.                                                                                                               |

#### Output

Program output defines how to transform the audio files. What tags to add, format conversions, and
output filename. Any capture groups from the parse regex can be referenced with curly braces
`{captureGroupName}`. Datetime capture groups can be referenced and formatted on any field with the
following syntax. `{parsedDate:yyyy-MM-dd}`
see [Date Time Formatter](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/format/DateTimeFormatter.html)
for a list of all format specifiers. Datetime math is also supported in the following way:

`{parsedDate+1y 2m 3d 4h 5m 6s:yyyy-MM-dd}` will add 1 year, 2 months, 3 days, 4 hours, 5 minutes,
and 6 seconds onto the date parsed from the named capture group `parsedDate` and format it with a 4
digit year, 2 digit month, and 2 digit day. Subtraction can be done with the `-` operator instead of
`+`.

| Option   | Type                | Default | Description                                                                                                         |
|----------|---------------------|---------|---------------------------------------------------------------------------------------------------------------------|
| format   | string              | null    | The output format to convert to. If no format is provided no file format conversions will be performed.             |
| filename | string              | null    | The filename (not including extension) to rename the file to. If not provided, the original file name will be used. |
| tags     | Map<String, String> | []      | Tags to tag the audio with. Program parse regex capture groups can be used in tag values.                           |
| limit    | Int                 | null    | How many items to download. Items older than the last limit item will be ignored.                                   |


In addition to any capture groups there are a few usable always available replacments:
- **old_filename**: The original file name
- **old_extension**: The original file extension
- **raw_filename**: The original file name.file extension
- **created_at**: A date of the creation timestamp of the file. Can be formatted etc.

#### Example

Bring all the program configuration together, here is what an example program using all the features might look like.

```yaml
fileSync:
  sources:
    - name: gfs
      type: NextCloud
      url: nextcloud.example.com
      username: steve
      password: secure!password
  programs:
    - name: American Indian Living
      source:
        name: gfs # Use the source named gfs
        path: /All Programs/American Indian Living
        depths: 3 # Look 3 levels deep for items
        extensions: # Only look at mp3 files
          - mp3
        parse:
          regex: AIL.*---(?<date>\d+-\d+-\d+).*for (?<playdate>\d+-\d+-\d+), (?<title>.*)(?<extension>\..+)
          dates:
            date: M-d-y # date has varying number of digits for month, day or year but is in a consistent order
            playdate: M-d-y # playdate has varying number of digits for month, day or year but is in a consistent order
          strict: true # If a file does not match fail the whole program
          entireMatch: true # Must fully match regex and not be a partial match
        output:
          format: mp3 # convert all files to mp3 no matter the source format
          filename: AIL_{playdate+1d:yyyy-MM-dd} # Put the play date in the file name which is +1 day of the original advertised play date.
          limit: 10 # Only look at the 10 latest matching items
          tags:
            genre: Program
            artist: Dr. Doofenshmirtz
            album: "American Indian Living {playdate:LLLL}"
            title: "{title} {date:yyyy-MM-dd}"
            comment: "{old_filename} at {created_at:yyyy-MM-dd}"
```

### Output

The output configuration section has the following options

| Option        | Type                | Default | Description                                                                                                              |
|---------------|---------------------|---------|--------------------------------------------------------------------------------------------------------------------------|
| dir           | string              | output  | The writeable directory to put all of the downloaded and transformed files into.                                         |
| ffmpegOptions | string              | null    | Extra command line options to pass to all items on processing.                                                           |
| id3Version    | string              | null    | ID3 version to use. If not specified ID3V2.3 is used. Valid options are: ID3_V22, ID3_V23, ID3_V24.                      |
| dryRun        | boolean             | false   | If dry run is enabled, items will not be downloaded or uploaded to the final output destination.                         |
| connector     | OutputConnectorSpec | null    | Final output connector. If not specified, files will be downloaded and transformed but not uploaded anywhere after that. |

#### OutputConnectorSpec

The output connector spec defines where to upload files to after they have been transformed.

| Option     | Type                | Default  | Description                                                                                                                              |
|------------|---------------------|----------|------------------------------------------------------------------------------------------------------------------------------------------|
| class      | string              | Required | The class of the output connector to load. Must extend the `com.persignum.filesync.gateways.OutputConnector` interface.                  |
| properties | Map<String, String> | []       | The properties to pass to the connector's constructor. All implementations must have a constructor that accepts a `Map<String, String>`. |

#### Example

```yaml
fileSync:
  output:
    ffmpegOptions: "-filter:a loudnorm=I=-23.0:offset=0.0" # Normalize all audio
    connector: # Upload to libretime
      class: com.persignum.filesync.gateways.LibreTimeApi
      properties:
        url: libretime.example.com
        apiKey: some_secret_api_key
```

## Supported Outputs

Currently the only supported output connector is `LibreTime`. Feel free to add your own! Just extend
the `com.persignum.filesync.gateways.OutputConnector` interface and make sure to have a constructor
that takes in a `Map<String, String>` for the properties.

### LibreTime

[LibreTime](https://libretime.org/) is a radio automation platform. You could automate pulling files from different sources
and have them automatically tagged and uploaded into LibreTime.

The LibreTime output connector class coordinate is `com.persignum.filesync.gateways.LibreTimeApi`.
the `url` and `apiKey` properties are required for this connector to work. It uploads files that
don't exist in your LibreTime instance.

## Supported Audio Tags

Below is a list of the supported audio tags. Not all audio tags are supported on all formats.

- acoustid_fingerprint
- acoustid_id
- album
- album_artist
- album_artist_sort
- album_artists
- album_artists_sort
- album_sort
- album_year
- amazon_id
- arranger
- arranger_sort
- artist
- artists
- artists_sort
- artist_sort
- barcode
- bpm
- catalog_no
- classical_catalog
- classical_nickname
- choir
- choir_sort
- comment
- composer
- composer_sort
- conductor
- conductor_sort
- copyright
- country
- cover_art
- custom1
- custom2
- custom3
- custom4
- custom5
- disc_no
- disc_subtitle
- disc_total
- djmixer
- djmixer_sort
- encoder
- engineer
- engineer_sort
- ensemble
- ensemble_sort
- fbpm
- genre
- group
- grouping
- instrument
- involvedpeople
- ipi
- isrc
- iswc
- is_classical
- is_greatest_hits
- is_hd
- is_live
- is_soundtrack
- is_compilation
- itunes_grouping
- jaikoz_id
- key
- language
- lyricist
- lyricist_sort
- lyrics
- media
- mixer
- mixer_sort
- mood
- mood_acoustic
- mood_aggressive
- mood_arousal
- mood_danceability
- mood_electronic
- mood_happy
- mood_instrumental
- mood_party
- mood_relaxed
- mood_sad
- mood_valence
- movement
- movement_no
- movement_total
- musicbrainz_artistid
- musicbrainz_disc_id
- musicbrainz_original_release_id
- musicbrainz_recording_work
- musicbrainz_recording_work_id
- musicbrainz_releaseartistid
- musicbrainz_releaseid
- musicbrainz_release_country
- musicbrainz_release_group_id
- musicbrainz_release_status
- musicbrainz_release_track_id
- musicbrainz_release_type
- musicbrainz_track_id
- musicbrainz_work
- musicbrainz_work_id
- musicbrainz_work_part_level1
- musicbrainz_work_part_level1_id
- musicbrainz_work_part_level1_type
- musicbrainz_work_part_level2
- musicbrainz_work_part_level2_id
- musicbrainz_work_part_level2_type
- musicbrainz_work_part_level3
- musicbrainz_work_part_level3_id
- musicbrainz_work_part_level3_type
- musicbrainz_work_part_level4
- musicbrainz_work_part_level4_id
- musicbrainz_work_part_level4_type
- musicbrainz_work_part_level5
- musicbrainz_work_part_level5_id
- musicbrainz_work_part_level5_type
- musicbrainz_work_part_level6
- musicbrainz_work_part_level6_id
- musicbrainz_work_part_level6_type
- musicip_id
- occasion
- opus
- orchestra
- orchestra_sort
- original_album
- originalreleasedate
- original_artist
- original_lyricist
- original_year
- overall_work
- part
- part_number
- part_type
- performer
- performer_name
- performer_name_sort
- period
- producer
- producer_sort
- quality
- ranking
- rating
- record_label
- recordingdate
- recordingstartdate
- recordingenddate
- recordinglocation
- remixer
- roonalbumtag
- roontracktag
- section
- script
- single_disc_track_no
- songkong_id
- subtitle
- tags
- tempo
- timbre
- title
- title_sort
- title_movement
- tonality
- track
- track_total
- url_discogs_artist_site
- url_discogs_release_site
- url_lyrics_site
- url_official_artist_site
- url_official_release_site
- url_wikipedia_artist_site
- url_wikipedia_release_site
- work
- work_type
- year
- version
