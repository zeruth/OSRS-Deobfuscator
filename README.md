# OSRS-Deobfuscator
--

A standalone OSRS deobfuscator/renamer

Future updates to RuneLite or OSRS may render this obsolete, but it currently works as of Revision 170.

--

Libraries:

asm-debug-all-5.2

guava-16.0.1

Always include latest runelite-api jar, and runescape-api jar from repo.runelite.net


--


Usage:

deob - run with command line argument of obfuscated gamepack, then output jar. Example:

Deob gamepack-170.jar deob-170.jar

updatemappings - run with command line argument of previous revision refactored gamepack, deob'd current gamepack, then output jar. Example:

UpdateMappings refactored-169.jar deob-170.jar refactored-170.jar
