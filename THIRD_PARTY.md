# Third-party material

- **adhan-java** (prayer time calculation): MIT licence, Batoul Apps. https://github.com/batoulapps/adhan-java
- **Weather data by [Open-Meteo.com](https://open-meteo.com/)**: licensed CC BY 4.0, free for non-commercial use. The
  app asks it for the current temperature of the chosen city.
- **Audio** (`app/src/main/res/raw/`), added by the project owner for personal use. The rights to these recordings
  have **not** been verified; the sources below are what the files' own tags say:
  - `adhan.mp3`: Makkah adhan, muezzin Ali Ibn Ahmed Mala. Tags credit www.assabile.com (2006).
  - `adhan_fajr.mp3`: Fajr adhan. The file names no source.
  - `iqama.mp3`: iqama by Mishary Rashid Alafasy. Tags credit alafasyinshad.blogspot.com.

  If you hold the rights to a recording and want it removed, please open an issue and it will be taken down.
  The app plays these when present and a short alert otherwise.

- `reminder.mp3`: reminder sound supplied by the app owner (the file he chose); its source and rights are not verified here.

## Afnon Android TV Launcher (layout idea for the "Mihrab" look)
The third look ("Mihrab") follows the screen layout idea of https://github.com/egyjs/afnon-android-tv-launcher
(MIT licence, Copyright (c) its authors): prayer cards in a row, favourite apps, and a mihrab arch with the next
prayer. No code or fonts were copied; the implementation (MihrabHome.java) is our own.
