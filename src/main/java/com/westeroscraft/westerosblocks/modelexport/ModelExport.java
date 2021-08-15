package com.westeroscraft.westerosblocks.modelexport;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.TreeSet;
import java.util.LinkedList;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.westeroscraft.westerosblocks.WesterosBlockDef;
import com.westeroscraft.westerosblocks.WesterosBlockLifecycle;
import com.westeroscraft.westerosblocks.WesterosBlocks;

import net.minecraft.block.Block;

public abstract class ModelExport {
    private static File destdir;
    private static File blockstatedir;
    private static File blockmodeldir;
    private static File itemmodeldir;
    private static boolean didInit = false;

    // Common state lists
    public static final String[] FACING = {  "north", "south", "east", "west" };
    public static final String[] BOOLEAN = {  "true", "false" };
    public static final String[] TOPBOTTOM = {  "top", "bottom" };
    public static final String[] UPPERLOWER = {  "upper", "lower" };
    public static final String[] LEFTRIGHT = {  "left", "right" };
    public static final String[] ALLFACING = {  "north", "south", "east", "west", "up", "down" };
    public static final String[] UPFACING = {  "north", "south", "east", "west", "up" };
    public static final String[] HEADFOOT = { "head", "foot" };
    public static final String[] AGE15 = { "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15" };
    public static final String[] DISTANCE7 = { "0", "1", "2", "3", "4", "5", "6", "7" };
    public static final String[] BITES7 = { "0", "1", "2", "3", "4", "5", "6", "7" };
    public static final String[] SHAPE5 = { "straight", "inner_right", "inner_left", "outer_right", "outer_left" };
    public static final String[] AXIS = { "x", "y", "z" };
    public static final String[] FACINGNE = {  "north", "east" };
    public static final String[] RAILSHAPE = { "north_south", "east_west", "ascending_east", "ascending_west", "ascending_north", "ascending_south","south_east", "south_west", "north_west", "north_east" };

    public static void doInit(File dest) {
    	if (!didInit) {
            destdir = dest;
            blockstatedir = new File(destdir, "assets/" + WesterosBlocks.MOD_ID + "/blockstates");
            blockstatedir.mkdirs();
            blockmodeldir = new File(destdir, "assets/" + WesterosBlocks.MOD_ID + "/models/block/generated");
            blockmodeldir.mkdirs();
            itemmodeldir = new File(destdir, "assets/" + WesterosBlocks.MOD_ID + "/models/item");
            itemmodeldir.mkdirs();
    		didInit = true;
    	}
    }
    
    protected final Block block;
    protected final WesterosBlockDef def;
    public ModelExport(Block block, WesterosBlockDef def, File dest) {
    	doInit(dest);
    	this.block = block;
    	this.def = def;
    }
    public void writeBlockStateFile(String blockname, Object obj) throws IOException {
        File f = new File(blockstatedir, blockname + ".json");
        FileWriter fos = null;
        try {
            fos = new FileWriter(f);
            Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
            gson.toJson(obj, fos);
        } finally {
            if (fos != null) {
                fos.close();
            }
        }
    }
    public void writeBlockModelFile(String model, Object obj) throws IOException {
        File f = new File(blockmodeldir, model + ".json");
        FileWriter fos = null;
        try {
            fos = new FileWriter(f);
            Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
            gson.toJson(obj, fos);
        } finally {
            if (fos != null) {
                fos.close();
            }
        }
    }
    public void writeItemModelFile(String model, Object obj) throws IOException {
        File f = new File(itemmodeldir, model + ".json");
        FileWriter fos = null;
        try {
            fos = new FileWriter(f);
            Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
            gson.toJson(obj, fos);
        } finally {
            if (fos != null) {
                fos.close();
            }
        }
    }
    public static String getTextureID(String id) {
        if (id.indexOf(':') >= 0) {
            return id;
        }
        else {
            return WesterosBlocks.MOD_ID + ":blocks/" + id;
        }
    }
    public abstract void doBlockStateExport() throws IOException;
    public abstract void doModelExports() throws IOException;
    public void doWorldConverterMigrate() throws IOException {
    	WesterosBlocks.log.info("No WoeldConverter support for " + def.blockType);
    }

    private static HashMap<String, String> nls = new HashMap<String, String>();
    public static void addNLSString(String id, String val) {
        nls.put(id, val);
    }
    public static void writeNLSFile(Path dest) throws IOException {
        File tgt = new File(dest.toFile(), "assets/" + WesterosBlocks.MOD_ID + "/lang");
        tgt.mkdirs();
        FileWriter fos = null;
        try {
            fos = new FileWriter(new File(tgt, "en_us.json"));
            Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();                
            gson.toJson(nls, fos);
        } finally {
            if (fos != null) {
                fos.close();
            }
        }
    }
    
    private static LinkedList<String> wcList = new LinkedList<String>();
    
    public static void addWorldConverterRecord(String oldBlockName, Map<String, String> oldBlockState, String newBlockName, Map<String, String> newBlockState) {
    	if (oldBlockName.indexOf(':') < 0) oldBlockName = WesterosBlocks.MOD_ID + ":" + oldBlockName;
    	if (newBlockName.indexOf(':') < 0) newBlockName = WesterosBlocks.MOD_ID + ":" + newBlockName;
    	StringBuilder sb = new StringBuilder(oldBlockName);
    	if ((oldBlockState != null) && (oldBlockState.size() > 0)) {
    		sb.append('[');
    		boolean first = true;
    		TreeSet<String> keys = new TreeSet<String>(oldBlockState.keySet());
    		for (String key : keys) {
    			if (!first) sb.append(',');
    			sb.append(key).append("=").append(oldBlockState.get(key));
    			first = false;
    		}
    		sb.append(']');    		
    	}
    	sb.append(" -> ");
    	sb.append(newBlockName);
    	if ((newBlockState != null) && (newBlockState.size() > 0)) {
    		sb.append('[');
    		boolean first = true;
    		TreeSet<String> keys = new TreeSet<String>(newBlockState.keySet());
    		for (String key : keys) {
    			if (!first) sb.append(',');
    			sb.append(key).append("=").append(newBlockState.get(key));
    			first = false;
    		}
    		sb.append(']');    		
    	}
    	wcList.add(sb.toString());
    }
    public static void addWorldConverterComment(String txt) {
    	//wcList.add("# " + txt);
    }
    public static void writeWorldConverterFile(Path dest) throws IOException {
        FileWriter fos = null;
        try {
            fos = new FileWriter(new File(dest.toFile(), "blocks_1.12-1.16__westerosblocks.txt"));
            for (String line : wcList) {
            	fos.write(line + "\r\n");
            }
        } finally {
            if (fos != null) {
                fos.close();
            }
        }
    }
    
    private static class TagFile {
    	boolean replace = false;
    	String[] values = {};
    };
    
    public static void writeTagDataFiles(Path dest) throws IOException {
        File tgt = new File(dest.toFile(), "data/minecraft/tags/blocks");
        tgt.mkdirs();
        HashMap<String, ArrayList<String>> blksByTag = new HashMap<String, ArrayList<String>>();
        // Load all the tags
        for (String blockName : WesterosBlocks.customBlocksByName.keySet()) {
        	Block blk = WesterosBlocks.customBlocksByName.get(blockName);
        	if (blk instanceof WesterosBlockLifecycle) {
        		WesterosBlockLifecycle wb = (WesterosBlockLifecycle) blk;
        		String[] tags = wb.getBlockTags();	// Get block tags
        		for (String tag : tags) {
        			ArrayList<String> lst = blksByTag.get(tag.toLowerCase());
        			if (lst == null) {
        				lst = new ArrayList<String>();
        				blksByTag.put(tag.toLowerCase(), lst);
        			}
        			lst.add(WesterosBlocks.MOD_ID + ":" + blockName);
        		}
        	}
        }
        // And write the files for each
        for (String tagID : blksByTag.keySet()) {
			ArrayList<String> lst = blksByTag.get(tagID);
	        FileWriter fos = null;
	        try {
	        	TagFile tf = new TagFile();
	        	tf.values = lst.toArray(new String[lst.size()]);
	            fos = new FileWriter(new File(tgt, tagID + ".json"));
	            Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();                
	            gson.toJson(tf, fos);
	        } finally {
	            if (fos != null) {
	                fos.close();
	            }
	        }			
        }
    }
    
    private static class TintOver {
        String cond;
        String txt;
    }
    private static Map<String, List<TintOver>> tintoverrides = new HashMap<String, List<TintOver>>();
    
    public static void addTintingOverride(String blockname, String cond, String txtfile) {
        List<TintOver> blst = tintoverrides.get(blockname);
        if (blst == null) {
            blst = new ArrayList<TintOver>();
            tintoverrides.put(blockname, blst);
        }
        TintOver to = new TintOver();
        to.cond = cond;
        to.txt = txtfile;
        blst.add(to);
    }
    public static void writeDynmapOverridesFile(Path dest) throws IOException {
        File tgt = new File(dest.toFile(), "assets/" + WesterosBlocks.MOD_ID + "/dynmap");
        tgt.mkdirs();
        PrintStream fw = null;
        try {
            fw = new PrintStream(new File(tgt, "blockstateoverrides.json"));
            fw.println("{");
            fw.println("  \"overrides\": {");
            fw.println("      \"" + WesterosBlocks.MOD_ID + "\": {");
            //TODO - add block state overrides
            fw.println("       }");
            fw.println("   },");
            fw.println("  \"tinting\": {");
            fw.println("      \"" + WesterosBlocks.MOD_ID + "\": {");
            boolean first1 = true;
            for (Entry<String, List<TintOver>> br : tintoverrides.entrySet()) {
                if (!first1) {
                    fw.println("          ,");
                }
                fw.println("          \"" + br.getKey() + "\": [");
                boolean first2 = true;
                for (TintOver toe : br.getValue()) {
                    if (!first2) {
                        fw.println("              ,");
                    }
                    if ((toe.cond != null) && (toe.cond.equals("") == false)) {
                        fw.println("              { \"state\": \"" + toe.cond + "\", \"colormap\": [ \"" + toe.txt + "\" ] }");
                    }
                    else {
                        fw.println("              { \"colormap\": [ \"" + toe.txt + "\" ] }");
                    }
                    first2 = false;
                }
                fw.println("          ]");
                first1 = false;
            }
            fw.println("       }");
            fw.println("   }");
            fw.println("}");
        } finally {
            if (fw != null) fw.close();
        }
    }

    public static class ForgeModel {
    	public Map<String, String> textures;
    	public String model;
    	public Boolean uvlock;
    	public Integer weight;
    }
    public static class ForgeDefaults extends ForgeModel {
    	public String transform = "forge:default-item";
    	public Boolean ambientocclusion;
    	public ForgeDefaults() {
    		textures = new HashMap<String, String>();
    	}
    }
    public static class ForgeBlockState {
    	public int forge_marker = 1;
    	public ForgeDefaults defaults = new ForgeDefaults();
    	public Map<String, AbstractForgeVariant> variants = new HashMap<String, AbstractForgeVariant>();
    }
    public static interface AbstractForgeVariant {
    }
    public static class ForgeVariantMap extends HashMap<String, ForgeVariant> implements AbstractForgeVariant {
    }
    public static class ForgeVariantList extends ArrayList<ForgeVariant> implements AbstractForgeVariant {
    }
    public static class ForgeVariant extends ForgeModel implements AbstractForgeVariant {
    	public Integer x, y, z;
    	public AbstractForgeSubmodel submodel;
    }
    public static interface AbstractForgeSubmodel {
    }
    public static class ForgeSubmodel extends ForgeModel implements AbstractForgeSubmodel {
    }
    public static class ForgeSubmodelMap extends HashMap<String, ForgeModel> implements AbstractForgeSubmodel {
    }
}
