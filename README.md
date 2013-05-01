ManyWorlds
===========

ManyWorlds is a world management MOD for Minecraft Forge servers.

Setup the source tree
---------------------

* git clone git://github.com/Taketsuru/ManyWorlds.git 
* Install Minecraft Forge 6.6.2 as described in
  http://www.minecraftforge.net/wiki/Installation/Source.
* Copy the contents of the installed forge tree to ManyWorlds/forge662.
  After this step, the directory structure is as follows.

<pre>
  ManyWorlds
  +--forge662/
  |  +--mcp/
  |     +--src/
  |        +--minecraft/
  |           +--cpw/
  |           +--ibxm/
  |            + ...
  |            +--taketsuru11/
  +--forge771/
</pre>

Build
-------------------------------------

The build procedure of ManyWorlds is the same as that of typical forge MODs.

* Run 'recompile.bat' at ManyWorlds/forge662/mcp.
* Run 'reobfuscate.bat' at ManyWorlds/forge662/mcp.
* Archive the files in ManyWorlds/forge662/mcp/reobf/minecraft and
  its subdirectories.

  The created ZIP file will have the following structure.

<pre>
  taketsuru1/
  +--manyworlds/
     +-- ...
</pre>

Install
-------

* Copy manyworlds.zip to 'mods' directory of the forge server and **CLIENTS**.

Current status
--------------

* The default configuration file creates dimension 2 and 4 (overworld) and dimension 3 and 5 (nether).
* Players can create a gate by creating 2x2 still water blocks framed by 12 obsidian blocks.  The gate is connected to dimension 2 when a player tosses a log wood by 'Q' key into the water blocks.  If a netherrack is tossed instead of a log wood, the gate will be connedted to dimension 3.
* Currently, both clients and server has to install the MOD.
* There is a directory for Forge 7.7.1 servers but it's not completed yet.
