package com.xilinx.rapidwright.interchange;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.Map.Entry;

import org.capnproto.MessageBuilder;
import org.capnproto.PrimitiveList;
import org.capnproto.SerializePacked;
import org.capnproto.StructList;
import org.capnproto.Text;
import org.capnproto.TextList;
import org.capnproto.PrimitiveList.Int;
import org.capnproto.Void;

import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.design.DesignTools;
import com.xilinx.rapidwright.design.SiteInst;
import com.xilinx.rapidwright.design.SitePinInst;
import com.xilinx.rapidwright.design.Unisim;
import com.xilinx.rapidwright.design.VivadoPropType;
import com.xilinx.rapidwright.design.VivadoProp;
import com.xilinx.rapidwright.device.Series;
import com.xilinx.rapidwright.device.BEL;
import com.xilinx.rapidwright.device.BELClass;
import com.xilinx.rapidwright.device.BELPin;
import com.xilinx.rapidwright.device.Device;
import com.xilinx.rapidwright.device.Node;
import com.xilinx.rapidwright.device.Package;
import com.xilinx.rapidwright.device.Grade;
import com.xilinx.rapidwright.device.PackagePin;
import com.xilinx.rapidwright.device.PIP;
import com.xilinx.rapidwright.device.PIPType;
import com.xilinx.rapidwright.device.PIPWires;
import com.xilinx.rapidwright.device.PseudoPIPHelper;
import com.xilinx.rapidwright.device.Site;
import com.xilinx.rapidwright.device.SitePIP;
import com.xilinx.rapidwright.device.SiteTypeEnum;
import com.xilinx.rapidwright.device.Tile;
import com.xilinx.rapidwright.device.TileTypeEnum;
import com.xilinx.rapidwright.device.Wire;
import com.xilinx.rapidwright.edif.EDIFCell;
import com.xilinx.rapidwright.edif.EDIFCellInst;
import com.xilinx.rapidwright.edif.EDIFDesign;
import com.xilinx.rapidwright.edif.EDIFLibrary;
import com.xilinx.rapidwright.edif.EDIFNetlist;
import com.xilinx.rapidwright.edif.EDIFPort;
import com.xilinx.rapidwright.edif.EDIFPortInst;
import com.xilinx.rapidwright.interchange.EnumerateCellBelMapping;
import com.xilinx.rapidwright.interchange.DeviceResources.Device.BELCategory;
import com.xilinx.rapidwright.interchange.DeviceResources.Device.BELInverter;
import com.xilinx.rapidwright.interchange.DeviceResources.Device.CellInversion;
import com.xilinx.rapidwright.interchange.DeviceResources.Device.CellPinInversion;
import com.xilinx.rapidwright.interchange.DeviceResources.Device.CellPinInversionParameter;
import com.xilinx.rapidwright.interchange.DeviceResources.Device.PrimToMacroExpansion;
import com.xilinx.rapidwright.interchange.DeviceResources.Device.PseudoCell;
import com.xilinx.rapidwright.interchange.DeviceResources.Device.SitePin;
import com.xilinx.rapidwright.interchange.DeviceResources.Device.SiteType;
import com.xilinx.rapidwright.interchange.DeviceResources.Device.SiteWire;
import com.xilinx.rapidwright.interchange.DeviceResources.Device.TileType;
import com.xilinx.rapidwright.interchange.DeviceResources.Device.BEL.Builder;
import com.xilinx.rapidwright.interchange.DeviceResources.Device.ParameterDefinition;
import com.xilinx.rapidwright.interchange.DeviceResources.Device.CellParameterDefinition;
import com.xilinx.rapidwright.interchange.DeviceResources.Device.ParameterDefinitions;
import com.xilinx.rapidwright.interchange.DeviceResources.Device.ParameterFormat;
import com.xilinx.rapidwright.interchange.LogicalNetlist.Netlist;
import com.xilinx.rapidwright.interchange.LogicalNetlist.Netlist.Direction;
import com.xilinx.rapidwright.interchange.LogicalNetlist.Netlist.PropertyMap;
import com.xilinx.rapidwright.tests.CodePerfTracker;

public class DeviceResourcesWriter {
    private static Enumerator<String> allStrings;
    private static Enumerator<String> allSiteTypes;

    private static HashMap<TileTypeEnum,Tile> tileTypes;
    private static HashMap<SiteTypeEnum,Site> siteTypes;

    public static void populateSiteEnumerations(SiteInst siteInst, Site site) {
        if(!siteTypes.containsKey(siteInst.getSiteTypeEnum())) {
            siteTypes.put(siteInst.getSiteTypeEnum(), site);
            allStrings.addObject(siteInst.getSiteTypeEnum().toString());

            for(String siteWire : siteInst.getSiteWires()) {
                allStrings.addObject(siteWire);
            }
            for(BEL bel : siteInst.getBELs()) {
                allStrings.addObject(bel.getName());
                allStrings.addObject(bel.getBELType());
                for(BELPin belPin : bel.getPins()) {
                    allStrings.addObject(belPin.getName());
                }
            }
            for(String sitePin : siteInst.getSitePinNames()) {
                allStrings.addObject(sitePin);
            }
        }
    }

    public static void populateEnumerations(Design design, Device device) {

        allStrings = new Enumerator<>();
        allSiteTypes = new Enumerator<>();

        tileTypes = new HashMap<>();
        siteTypes = new HashMap<>();
        for(Tile tile : device.getAllTiles()) {
            allStrings.addObject(tile.getName());
            if(!tileTypes.containsKey(tile.getTileTypeEnum())) {
                allStrings.addObject(tile.getTileTypeEnum().name());
                for(int i=0; i < tile.getWireCount(); i++) {
                    allStrings.addObject(tile.getWireName(i));
                }
                tileTypes.put(tile.getTileTypeEnum(),tile);
            }
            for(Site site : tile.getSites()) {
                allStrings.addObject(site.getName());
                allStrings.addObject(site.getSiteTypeEnum().name());
                SiteInst siteInst = design.createSiteInst("site_instance", site.getSiteTypeEnum(), site);
                populateSiteEnumerations(siteInst, site);
                design.removeSiteInst(siteInst);

                SiteTypeEnum[] altSiteTypes = site.getAlternateSiteTypeEnums();
                for(int i=0; i < altSiteTypes.length; i++) {
                    SiteInst altSiteInst = design.createSiteInst("site_instance", altSiteTypes[i], site);
                    populateSiteEnumerations(altSiteInst, site);
                    design.removeSiteInst(altSiteInst);
                }
            }

        }
        for(Entry<String,String> e : EDIFNetlist.macroExpandExceptionMap.entrySet()) {
            allStrings.addObject(e.getKey());
            allStrings.addObject(e.getValue());
        }
    }

    private static void writeCellParameterDefinitions(Series series, EDIFNetlist prims, ParameterDefinitions.Builder builder) {
        Set<String> cellsWithParameters = new HashSet<String>();
        for(EDIFLibrary library : prims.getLibraries()) {
            for(EDIFCell cell : library.getCells()) {
                String cellTypeName = cell.getName();

                Map<String,VivadoProp> defaultCellProperties = Design.getDefaultCellProperties(series, cellTypeName);
                if(defaultCellProperties != null && defaultCellProperties.size() > 0) {
                    cellsWithParameters.add(cellTypeName);
                }
            }
        }

        StructList.Builder<CellParameterDefinition.Builder> cellParamDefs = builder.initCells(cellsWithParameters.size());
        int i = 0;
        for(String cellTypeName : cellsWithParameters) {
            CellParameterDefinition.Builder cellParamDef = cellParamDefs.get(i);
            i += 1;


            cellParamDef.setCellType(allStrings.getIndex(cellTypeName));
            Map<String,VivadoProp> defaultCellProperties = Design.getDefaultCellProperties(series, cellTypeName);

            StructList.Builder<ParameterDefinition.Builder> paramDefs = cellParamDef.initParameters(defaultCellProperties.size());
            int j = 0;
            for(Map.Entry<String, VivadoProp> property : defaultCellProperties.entrySet()) {
                ParameterDefinition.Builder paramDef = paramDefs.get(j);
                j += 1;

                String propName = property.getKey();
                VivadoProp propValue = property.getValue();

                Integer nameIdx = allStrings.getIndex(propName);
                paramDef.setName(nameIdx);

                PropertyMap.Entry.Builder defaultValue = paramDef.getDefault();
                defaultValue.setKey(nameIdx);
                defaultValue.setTextValue(allStrings.getIndex(propValue.getValue()));

                if(propValue.getType() == VivadoPropType.BINARY) {
                    paramDef.setFormat(ParameterFormat.VERILOG_BINARY);
                } else if(propValue.getType() == VivadoPropType.BOOL) {
                    paramDef.setFormat(ParameterFormat.BOOLEAN);
                } else if(propValue.getType() == VivadoPropType.DOUBLE) {
                    paramDef.setFormat(ParameterFormat.FLOATING_POINT);
                } else if(propValue.getType() == VivadoPropType.HEX) {
                    paramDef.setFormat(ParameterFormat.VERILOG_HEX);
                } else if(propValue.getType() == VivadoPropType.INT) {
                    paramDef.setFormat(ParameterFormat.INTEGER);
                } else if(propValue.getType() == VivadoPropType.STRING) {
                    paramDef.setFormat(ParameterFormat.STRING);
                } else {
                    throw new RuntimeException(String.format("Unknown VivadoPropType %s", propValue.getType().name()));
                }
            }
        }
    }


    public static void writeDeviceResourcesFile(String part, Device device, CodePerfTracker t,
                                                                String fileName) throws IOException {
        Design design = new Design();
        design.setPartName(part);

        t.start("populateEnums");
        populateEnumerations(design, device);

        MessageBuilder message = new MessageBuilder();
        DeviceResources.Device.Builder devBuilder = message.initRoot(DeviceResources.Device.factory);
        devBuilder.setName(device.getName());

        t.stop().start("SiteTypes");
        writeAllSiteTypesToBuilder(design, device, devBuilder);

        t.stop().start("TileTypes");
        Map<TileTypeEnum, Integer> tileTypeIndicies = writeAllTileTypesToBuilder(design, device, devBuilder);
        Map<TileTypeEnum, TileType.Builder> tileTypesObj = new HashMap<TileTypeEnum, TileType.Builder>();
        for(Map.Entry<TileTypeEnum, Integer> tileType : tileTypeIndicies.entrySet()) {
            tileTypesObj.put(tileType.getKey(), devBuilder.getTileTypeList().get(tileType.getValue()));
        }

        t.stop().start("Tiles");
        writeAllTilesToBuilder(device, devBuilder, tileTypeIndicies);

        t.stop().start("Wires&Nodes");
        writeAllWiresAndNodesToBuilder(device, devBuilder);

        t.stop().start("Prims&Macros");
        // Create an EDIFNetlist populated with just primitive and macro libraries
        EDIFLibrary prims = Design.getPrimitivesLibrary(device.getName());
        EDIFLibrary macros = Design.getMacroPrimitives(device.getSeries());
        EDIFNetlist netlist = new EDIFNetlist("PrimitiveLibs");
        netlist.addLibrary(prims);
        netlist.addLibrary(macros);
        List<EDIFCell> dupsToRemove = new ArrayList<EDIFCell>();
        for(EDIFCell hdiCell : prims.getCells()) {
            EDIFCell cell = macros.getCell(hdiCell.getName());
            if(cell != null) {
                dupsToRemove.add(hdiCell);
            }
        }

        for(EDIFCell dupCell : dupsToRemove) {
            prims.removeCell(dupCell);
        }

        for(EDIFCell cell : macros.getCells()) {
            for(EDIFCellInst inst : cell.getCellInsts()) {
                EDIFCell instCell = inst.getCellType();
                EDIFCell macroCell = macros.getCell(instCell.getName());
                if(macroCell != null) {
                    // remap cell definition to macro library
                    inst.updateCellType(macroCell);
                }
            }
        }

        List<Unisim> unisims = new ArrayList<Unisim>();
        for(EDIFCell cell : macros.getCells()) {
            String cellName = cell.getName();
            String primName = EDIFNetlist.macroCollapseExceptionMap.get(cellName);
            if(primName != null) {
                cellName = primName;
            }
            Unisim unisim = Unisim.valueOf(cellName);
            Map<String,String> invertiblePins = DesignTools.getInvertiblePinMap(device.getSeries(), unisim);
            if(invertiblePins != null && invertiblePins.size() > 0) {
                unisims.add(unisim);
            }
        }
        for(EDIFCell cell : prims.getCells()) {
            Unisim unisim = Unisim.valueOf(cell.getName());
            Map<String,String> invertiblePins = DesignTools.getInvertiblePinMap(device.getSeries(), unisim);
            if(invertiblePins != null && invertiblePins.size() > 0) {
                unisims.add(unisim);
            }
        }

        StructList.Builder<CellInversion.Builder> cellInversions = devBuilder.initCellInversions(unisims.size());
        for(int i = 0; i < unisims.size(); ++i) {
            Unisim unisim = unisims.get(i);
            CellInversion.Builder cellInversion = cellInversions.get(i);
            cellInversion.setCell(allStrings.getIndex(unisim.name()));

            Map<String,String> invertiblePins = DesignTools.getInvertiblePinMap(device.getSeries(), unisim);
            StructList.Builder<CellPinInversion.Builder> cellPinInversions = cellInversion.initCellPins(invertiblePins.size());

            int j = 0;
            for(Map.Entry<String, String> entry : invertiblePins.entrySet()) {
                String port = entry.getKey();
                String parameterStr = entry.getValue();

                CellPinInversion.Builder pinInversion = cellPinInversions.get(j);
                j += 1;

                pinInversion.setCellPin(allStrings.getIndex(port));

                CellPinInversionParameter.Builder param = pinInversion.getNotInverting();
                PropertyMap.Entry.Builder parameter = param.initParameter();
                parameter.setKey(allStrings.getIndex(parameterStr));
                parameter.setTextValue(allStrings.getIndex("FALSE"));

                param = pinInversion.getInverting();
                parameter = param.initParameter();
                parameter.setKey(allStrings.getIndex(parameterStr));
                parameter.setTextValue(allStrings.getIndex("TRUE"));
            }
        }

        Netlist.Builder netlistBuilder = devBuilder.getPrimLibs();
        netlistBuilder.setName(netlist.getName());
        LogNetlistWriter writer = new LogNetlistWriter(allStrings);
        writer.populateNetlistBuilder(netlist, netlistBuilder);

        writeCellParameterDefinitions(device.getSeries(), netlist, devBuilder.getParameterDefs());

        // Write macro exception map
        int size = EDIFNetlist.macroExpandExceptionMap.size();
        StructList.Builder<PrimToMacroExpansion.Builder> exceptionMap =
                devBuilder.initExceptionMap(size);
        int i=0;
        for(Entry<String, String> entry : EDIFNetlist.macroExpandExceptionMap.entrySet()) {
            PrimToMacroExpansion.Builder entryBuilder = exceptionMap.get(i);
            entryBuilder.setMacroName(allStrings.getIndex(entry.getValue()));
            entryBuilder.setPrimName(allStrings.getIndex(entry.getKey()));
            i++;
        }

        t.stop().start("Cell <-> BEL pin map");
        EnumerateCellBelMapping.populateAllPinMappings(part, device, devBuilder, allStrings);

        t.stop().start("Packages");
        populatePackages(allStrings, device, devBuilder);

        t.stop().start("Constants");
        ConstantDefinitions.writeConstants(allStrings, device, devBuilder.initConstants(), design, siteTypes, tileTypesObj);

        t.stop().start("Strings");
        writeAllStringsToBuilder(devBuilder);

        t.stop().start("Write File");
        Interchange.writeInterchangeFile(fileName, message);
        t.stop();
    }

    public static void writeAllStringsToBuilder(DeviceResources.Device.Builder devBuilder) {
        int stringCount = allStrings.size();
        TextList.Builder strList = devBuilder.initStrList(stringCount);
        for(int i=0; i < stringCount; i++) {
            strList.set(i, new Text.Reader(allStrings.get(i)));
        }
    }

    protected static BELCategory getBELCategory(BEL bel) {
        BELClass category = bel.getBELClass();
        if(category == BELClass.BEL)
            return BELCategory.LOGIC;
        if(category == BELClass.RBEL)
            return BELCategory.ROUTING;
        if(category == BELClass.PORT)
            return BELCategory.SITE_PORT;
        return BELCategory._NOT_IN_SCHEMA;
    }

    protected static Direction getBELPinDirection(BELPin belPin) {
        BELPin.Direction dir = belPin.getDir();
        if(dir == BELPin.Direction.INPUT)
            return Direction.INPUT;
        if(dir == BELPin.Direction.OUTPUT)
            return Direction.OUTPUT;
        if(dir == BELPin.Direction.BIDIRECTIONAL)
            return Direction.INOUT;
        return Direction._NOT_IN_SCHEMA;
    }

    public static void writeAllSiteTypesToBuilder(Design design, Device device, DeviceResources.Device.Builder devBuilder) {
        StructList.Builder<SiteType.Builder> siteTypesList = devBuilder.initSiteTypeList(siteTypes.size());

        int i=0;
        for(Entry<SiteTypeEnum,Site> e : siteTypes.entrySet()) {
            SiteType.Builder siteType = siteTypesList.get(i);
            Site site = e.getValue();
            SiteInst siteInst = design.createSiteInst("site_instance", e.getKey(), site);
            Tile tile = siteInst.getTile();
            siteType.setName(allStrings.getIndex(e.getKey().name()));
            allSiteTypes.addObject(e.getKey().name());

            Enumerator<BELPin> allBELPins = new Enumerator<BELPin>();
            Enumerator<SitePIP> allSitePIPs = new Enumerator<SitePIP>();

            // BELs
            StructList.Builder<Builder> belBuilders = siteType.initBels(siteInst.getBELs().length);
            for(int j=0; j < siteInst.getBELs().length; j++) {
                BEL bel = siteInst.getBELs()[j];
                Builder belBuilder = belBuilders.get(j);
                belBuilder.setName(allStrings.getIndex(bel.getName()));
                belBuilder.setType(allStrings.getIndex(bel.getBELType()));
                PrimitiveList.Int.Builder belPinsBuilder = belBuilder.initPins(bel.getPins().length);
                for(int k=0; k < bel.getPins().length; k++) {
                    BELPin belPin = bel.getPin(k);
                    belPinsBuilder.set(k, allBELPins.getIndex(belPin));
                }
                belBuilder.setCategory(getBELCategory(bel));

                if(bel.canInvert()) {
                    BELInverter.Builder belInverter = belBuilder.initInverting();
                    belInverter.setNonInvertingPin(allBELPins.getIndex(bel.getNonInvertingPin()));
                    belInverter.setInvertingPin(allBELPins.getIndex(bel.getInvertingPin()));
                } else {
                    belBuilder.setNonInverting(Void.VOID);
                }
            }

            // SitePins
            int highestIndexInputPin = siteInst.getHighestSitePinInputIndex();
            ArrayList<String> pinNames = new ArrayList<String>();
            for(String pinName : siteInst.getSitePinNames()) {
                pinNames.add(pinName);
            }
            siteType.setLastInput(highestIndexInputPin);

            StructList.Builder<SitePin.Builder> pins = siteType.initPins(pinNames.size());
            for(int j=0; j < pinNames.size(); j++) {
                String primarySitePinName = pinNames.get(j);
                int sitePinIndex = site.getPinIndex(pinNames.get(j));
                if(sitePinIndex == -1) {
                    primarySitePinName = siteInst.getPrimarySitePinName(pinNames.get(j));
                    sitePinIndex = site.getPinIndex(primarySitePinName);
                }

                if(sitePinIndex == -1) {
                    throw new RuntimeException("Failed to find pin index for site " + site.getName() + " site type " + e.getKey().name()+ " site pin " + primarySitePinName + " / " + pinNames.get(j));
                }

                SitePin.Builder pin = pins.get(j);
                pin.setName(allStrings.getIndex(pinNames.get(j)));
                pin.setDir(j <= highestIndexInputPin ? Direction.INPUT : Direction.OUTPUT);
                BEL bel = siteInst.getBEL(pinNames.get(j));
                BELPin[] belPins = bel.getPins();
                if(belPins.length != 1) {
                    throw new RuntimeException("Only expected 1 BEL pin on site pin BEL.");
                }
                BELPin belPin = belPins[0];
                pin.setBelpin(allBELPins.getIndex(belPin));
            }

            // SiteWires
            String[] siteWires = siteInst.getSiteWires();
            StructList.Builder<SiteWire.Builder> swBuilders =
                    siteType.initSiteWires(siteWires.length);
            for(int j=0; j < siteWires.length; j++) {
                SiteWire.Builder swBuilder = swBuilders.get(j);
                String siteWireName = siteWires[j];
                swBuilder.setName(allStrings.getIndex(siteWireName));
                BELPin[] swPins = siteInst.getSiteWirePins(siteWireName);
                PrimitiveList.Int.Builder bpBuilders = swBuilder.initPins(swPins.length);
                for(int k=0; k < swPins.length; k++) {
                    bpBuilders.set(k, allBELPins.getIndex(swPins[k]));
                }
            }

            // Write out BEL pins.
            StructList.Builder<DeviceResources.Device.BELPin.Builder> belPinBuilders =
                    siteType.initBelPins(allBELPins.size());
            for(int j=0; j < allBELPins.size(); j++) {
                DeviceResources.Device.BELPin.Builder belPinBuilder = belPinBuilders.get(j);
                BELPin belPin = allBELPins.get(j);
                belPinBuilder.setName(allStrings.getIndex(belPin.getName()));
                belPinBuilder.setDir(getBELPinDirection(belPin));
                belPinBuilder.setBel(allStrings.getIndex(belPin.getBEL().getName()));

                SitePIP sitePip = siteInst.getSitePIP(belPin);
                if(sitePip != null) {
                    allSitePIPs.addObject(sitePip);
                }
            }

            // Write out SitePIPs
            StructList.Builder<DeviceResources.Device.SitePIP.Builder> spBuilders =
                    siteType.initSitePIPs(allSitePIPs.size());
            for(int j=0; j < allSitePIPs.size(); j++) {
                DeviceResources.Device.SitePIP.Builder spBuilder = spBuilders.get(j);
                SitePIP sitePIP = allSitePIPs.get(j);
                spBuilder.setInpin(allBELPins.getIndex(sitePIP.getInputPin()));
                spBuilder.setOutpin(allBELPins.getIndex(sitePIP.getOutputPin()));
            }

            design.removeSiteInst(siteInst);
            i++;
        }

        i = 0;
        for(Entry<SiteTypeEnum,Site> e : siteTypes.entrySet()) {
            Site site = e.getValue();

            SiteType.Builder siteType = siteTypesList.get(i);

            SiteTypeEnum[] altSiteTypes = site.getAlternateSiteTypeEnums();
            PrimitiveList.Int.Builder altSiteTypesBuilder = siteType.initAltSiteTypes(altSiteTypes.length);

            for(int j=0; j < altSiteTypes.length; ++j) {
                Integer siteTypeIdx = allSiteTypes.maybeGetIndex(altSiteTypes[j].name());
                if(siteTypeIdx == null) {
                    throw new RuntimeException("Site type " + altSiteTypes[j].name() + " is missing from allSiteTypes Enumerator.");
                }
                altSiteTypesBuilder.set(j, siteTypeIdx);
            }

            i++;
        }
    }

    private static void populateAltSitePins(
            Design design,
            Site site,
            int primaryTypeIndex,
            StructList.Builder<DeviceResources.Device.ParentPins.Builder> listOfParentPins,
            DeviceResources.Device.Builder devBuilder) {
        PrimitiveList.Int.Builder altSiteTypes = devBuilder.getSiteTypeList().get(primaryTypeIndex).getAltSiteTypes();
        SiteTypeEnum[] altSiteTypeEnums = site.getAlternateSiteTypeEnums();
        for(int i = 0; i < altSiteTypeEnums.length; ++i) {
            SiteInst siteInst = design.createSiteInst("site_instance", altSiteTypeEnums[i], site);

            DeviceResources.Device.SiteType.Builder altSiteType = devBuilder.getSiteTypeList().get(altSiteTypes.get(i));
            StructList.Builder<DeviceResources.Device.SitePin.Builder> sitePins = altSiteType.getPins();
            PrimitiveList.Int.Builder parentPins = listOfParentPins.get(i).initPins(altSiteType.getPins().size());

            for(int j = 0; j < sitePins.size(); j++) {
                DeviceResources.Device.SitePin.Builder sitePin = sitePins.get(j);
                String sitePinName = allStrings.get(sitePin.getName());
                String parentPinName = siteInst.getPrimarySitePinName(sitePinName);
                parentPins.set(j, site.getPinIndex(parentPinName));
            }

            design.removeSiteInst(siteInst);
        }
    }

    public static Map<TileTypeEnum, Integer> writeAllTileTypesToBuilder(Design design, Device device, DeviceResources.Device.Builder devBuilder) {
        StructList.Builder<TileType.Builder> tileTypesList = devBuilder.initTileTypeList(tileTypes.size());

        Map<TileTypeEnum, Integer> tileTypeIndicies = new HashMap<TileTypeEnum, Integer>();

        int i=0;
        for(Entry<TileTypeEnum,Tile> e : tileTypes.entrySet()) {
            Tile tile = e.getValue();
            TileType.Builder tileType = tileTypesList.get(i);
            tileTypeIndicies.put(e.getKey(), i);
            // name
            tileType.setName(allStrings.getIndex(e.getKey().name()));

            // siteTypes
            Site[] sites = tile.getSites();
            StructList.Builder<DeviceResources.Device.SiteTypeInTileType.Builder> siteTypes = tileType.initSiteTypes(sites.length);
            for(int j=0; j < sites.length; j++) {
                DeviceResources.Device.SiteTypeInTileType.Builder siteType = siteTypes.get(j);
                int primaryTypeIndex = allSiteTypes.getIndex(sites[j].getSiteTypeEnum().name());
                siteType.setPrimaryType(primaryTypeIndex);

                int numPins = sites[j].getSitePinCount();
                PrimitiveList.Int.Builder pinWires = siteType.initPrimaryPinsToTileWires(numPins);
                for(int k=0; k < numPins; ++k) {
                    pinWires.set(k, allStrings.getIndex(sites[j].getTileWireNameFromPinName(sites[j].getPinName(k))));
                }

                populateAltSitePins(
                        design,
                        sites[j],
                        primaryTypeIndex,
                        siteType.initAltPinsToPrimaryPins(sites[j].getAlternateSiteTypeEnums().length),
                        devBuilder);
            }

            // wires
            PrimitiveList.Int.Builder wires = tileType.initWires(tile.getWireCount());
            for(int j=0 ; j < tile.getWireCount(); j++) {
                wires.set(j, allStrings.getIndex(tile.getWireName(j)));
            }

            // pips
            ArrayList<PIP> pips = tile.getPIPs();
            StructList.Builder<DeviceResources.Device.PIP.Builder> pipBuilders =
                    tileType.initPips(pips.size());
            for(int j=0; j < pips.size(); j++) {
                DeviceResources.Device.PIP.Builder pipBuilder = pipBuilders.get(j);
                PIP pip = pips.get(j);
                pipBuilder.setWire0(pip.getStartWireIndex());
                pipBuilder.setWire1(pip.getEndWireIndex());
                pipBuilder.setDirectional(!pip.isBidirectional());
                if(pip.getPIPType() == PIPType.BI_DIRECTIONAL_BUFFERED20) {
                    pipBuilder.setBuffered20(true);
                } else if(pip.getPIPType() == PIPType.BI_DIRECTIONAL_BUFFERED21_BUFFERED20) {
                    pipBuilder.setBuffered20(true);
                    pipBuilder.setBuffered21(true);
                } else if(pip.getPIPType() == PIPType.DIRECTIONAL_BUFFERED21) {
                    pipBuilder.setBuffered21(true);
                }

                if(pip.isRouteThru()) {
                    PseudoPIPHelper pseudoPIPHelper = PseudoPIPHelper.getPseudoPIPHelper(pip);
                    List<BELPin> belPins = pseudoPIPHelper.getUsedBELPins();

                    HashMap<BEL,ArrayList<BELPin>> pins = new HashMap<BEL, ArrayList<BELPin>>();
                    for(BELPin pin : belPins) {
                        ArrayList<BELPin> currBELPins = pins.get(pin.getBEL());
                        if(currBELPins == null) {
                            currBELPins = new ArrayList<>();
                            pins.put(pin.getBEL(), currBELPins);
                        }
                        currBELPins.add(pin);
                    }
                    StructList.Builder<PseudoCell.Builder> pseudoCells = pipBuilder.initPseudoCells(pins.size());
                    int k=0;
                    for(Entry<BEL, ArrayList<BELPin>> e3 : pins.entrySet()) {
                        PseudoCell.Builder pseudoCell = pseudoCells.get(k);
                        pseudoCell.setBel(allStrings.getIndex(e3.getKey().getName()));
                        List<BELPin> usedPins = e3.getValue();
                        int pinCount = usedPins.size();
                        Int.Builder pinsBuilder = pseudoCell.initPins(pinCount);
                        for(int l=0; l < pinCount; l++) {
                            pinsBuilder.set(l, allStrings.getIndex(usedPins.get(l).getName()));
                        }
                        k++;
                    }
                }
            }
            i++;
        }

        return tileTypeIndicies;
    }

    public static void writeAllTilesToBuilder(Device device, DeviceResources.Device.Builder devBuilder, Map<TileTypeEnum, Integer> tileTypeIndicies) {
        Collection<Tile> tiles = device.getAllTiles();
        StructList.Builder<DeviceResources.Device.Tile.Builder> tileBuilders =
                devBuilder.initTileList(tiles.size());

        int i=0;
        for(Tile tile : tiles) {
            DeviceResources.Device.Tile.Builder tileBuilder = tileBuilders.get(i);
            tileBuilder.setName(allStrings.getIndex(tile.getName()));
            tileBuilder.setType(tileTypeIndicies.get(tile.getTileTypeEnum()));
            Site[] sites = tile.getSites();
            StructList.Builder<DeviceResources.Device.Site.Builder> siteBuilders =
                    tileBuilder.initSites(sites.length);
            for(int j=0; j < sites.length; j++) {
                DeviceResources.Device.Site.Builder siteBuilder = siteBuilders.get(j);
                siteBuilder.setName(allStrings.getIndex(sites[j].getName()));
                siteBuilder.setType(j);
            }
            tileBuilder.setRow((short)tile.getRow());
            tileBuilder.setCol((short)tile.getColumn());
            i++;
        }

    }

    private static long makeKey(Tile tile, int wire) {
        long key = wire;
        key = (((long)tile.getUniqueAddress()) << 32) | key;
        return key;
    }

    public static void writeAllWiresAndNodesToBuilder(Device device, DeviceResources.Device.Builder devBuilder) {
        LongEnumerator allWires = new LongEnumerator();
        LongEnumerator allNodes = new LongEnumerator();


        for(Tile tile : device.getAllTiles()) {
            for(int i=0; i < tile.getWireCount(); i++) {
                Wire wire = new Wire(tile,i);
                allWires.addObject(makeKey(wire.getTile(), wire.getWireIndex()));

                Node node = wire.getNode();
                if(node != null) {
                    allNodes.addObject(makeKey(node.getTile(), node.getWire()));
                }
            }

            for(PIP p : tile.getPIPs()) {
                Node start = p.getStartNode();
                if(start != null) {
                    allNodes.addObject(makeKey(start.getTile(), start.getWire()));
                }
                Node end = p.getEndNode();
                if(end != null) {
                    allNodes.addObject(makeKey(end.getTile(), end.getWire()));
                }
            }
        }

        StructList.Builder<DeviceResources.Device.Wire.Builder> wireBuilders =
                devBuilder.initWires(allWires.size());

        for(int i=0; i < allWires.size(); i++) {
            DeviceResources.Device.Wire.Builder wireBuilder = wireBuilders.get(i);
            long wireKey = allWires.get(i);
            Wire wire = new Wire(device.getTile((int)(wireKey >>> 32)), (int)(wireKey & 0xffffffff));
            //Wire wire = allWires.get(i);
            wireBuilder.setTile(allStrings.getIndex(wire.getTile().getName()));
            wireBuilder.setWire(allStrings.getIndex(wire.getWireName()));
        }

        StructList.Builder<DeviceResources.Device.Node.Builder> nodeBuilders =
                devBuilder.initNodes(allNodes.size());
        for(int i=0; i < allNodes.size(); i++) {
            DeviceResources.Device.Node.Builder nodeBuilder = nodeBuilders.get(i);
            //Node node = allNodes.get(i);
            long nodeKey = allNodes.get(i);
            Node node = Node.getNode(device.getTile((int)(nodeKey >>> 32)), (int)(nodeKey & 0xffffffff));
            Wire[] wires = node.getAllWiresInNode();
            PrimitiveList.Int.Builder wBuilders = nodeBuilder.initWires(wires.length);
            for(int k=0; k < wires.length; k++) {
                wBuilders.set(k, allWires.getIndex(makeKey(wires[k].getTile(), wires[k].getWireIndex())));
            }
        }
    }
    private static void populatePackages(Enumerator<String> allStrings, Device device, DeviceResources.Device.Builder devBuilder) {
        Set<String> packages = device.getPackages();
        List<String> packagesList = new ArrayList<String>();
        packagesList.addAll(packages);
        packagesList.sort(new EnumerateCellBelMapping.StringCompare());
        StructList.Builder<DeviceResources.Device.Package.Builder> packagesObj = devBuilder.initPackages(packages.size());

        for(int i = 0; i < packages.size(); ++i) {
            Package pack = device.getPackage(packagesList.get(i));
            DeviceResources.Device.Package.Builder packageBuilder = packagesObj.get(i);

            packageBuilder.setName(allStrings.getIndex(pack.getName()));

            LinkedHashMap<String,PackagePin> packagePinMap = pack.getPackagePinMap();
            List<String> packagePins = new ArrayList<String>();
            packagePins.addAll(packagePinMap.keySet());
            packagePins.sort(new EnumerateCellBelMapping.StringCompare());

            StructList.Builder<DeviceResources.Device.Package.PackagePin.Builder> packagePinsObj = packageBuilder.initPackagePins(packagePins.size());
            for(int j = 0; j < packagePins.size(); ++j) {
                PackagePin packagePin = packagePinMap.get(packagePins.get(j));
                DeviceResources.Device.Package.PackagePin.Builder packagePinObj = packagePinsObj.get(j);

                packagePinObj.setPackagePin(allStrings.getIndex(packagePin.getName()));
                Site site = packagePin.getSite();
                if(site != null) {
                    packagePinObj.initSite().setSite(allStrings.getIndex(site.getName()));
                } else {
                    packagePinObj.initSite().setNoSite(Void.VOID);
                }

                BEL bel = packagePin.getBEL();
                if(bel != null) {
                    packagePinObj.initBel().setBel(allStrings.getIndex(bel.getName()));
                } else {
                    packagePinObj.initBel().setNoBel(Void.VOID);
                }
            }

            StructList.Builder<DeviceResources.Device.Package.Grade.Builder> grades = packageBuilder.initGrades(pack.getGrades().length);
            for(int j = 0; j < pack.getGrades().length; ++j) {
                Grade grade = pack.getGrades()[j];
                DeviceResources.Device.Package.Grade.Builder gradeObj = grades.get(j);
                gradeObj.setName(allStrings.getIndex(grade.getName()));
                gradeObj.setSpeedGrade(allStrings.getIndex(grade.getSpeedGrade()));
                gradeObj.setTemperatureGrade(allStrings.getIndex(grade.getTemperatureGrade()));
            }
        }
    }
}
