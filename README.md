# OSRS-Deobfuscator
--

A standalone OSRS deobfuscator/refactoring tool

Deobfuscator - Removes unnecessary code from vanilla gamepack

UpdateMappings - Refactors the deobfuscated gamepack using https://github.com/zeruth/runescape-client

--

Libraries:

asm-debug-all-5.2

guava-16.0.1

Always include latest runelite-api jar, and runescape-api jar from repo.runelite.net

Always include latest runescape-client.jar from https://github.com/zeruth/runescape-client


--


Usage:

deob - run with command line argument of obfuscated gamepack, then output jar. Example:

Deob gamepack-170.jar deob-170.jar

updatemappings - run with command line argument of current revision gamepack, current runescape-client.jar, then output jar. Example:

UpdateMappings deob-170.jar runescape-client.jar Refactored-170.jar
