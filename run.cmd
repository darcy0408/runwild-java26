@echo off
REM RunWild launcher.
REM  - chcp 65001 + stdout.encoding put the console into UTF-8 so the score bars,
REM    degree signs and micrograms render instead of turning into question marks.
REM  - --enable-preview is required: StructuredTaskScope is a preview API in Java 26.
chcp 65001 >nul
java --enable-preview -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -jar "%~dp0target\runwild.jar" %*
