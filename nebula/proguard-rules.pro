# Nebula base app: no reflection-based serialization; keep defaults.
# Match V+: the engine is open source (GPL-3.0), so obfuscation buys nothing and makes crash
# reports unreadable; parts of the V+ core also rely on reflection that renaming breaks.
-dontobfuscate
