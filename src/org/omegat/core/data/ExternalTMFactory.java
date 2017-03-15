/**************************************************************************
 OmegaT - Computer Assisted Translation (CAT) tool 
          with fuzzy matching, translation memory, keyword search, 
          glossaries, and translation leveraging into updated projects.

 Copyright (C) 2017 Aaron Madlon-Kay
               Home page: http://www.omegat.org/
               Support center: http://groups.yahoo.com/group/OmegaT/

 This file is part of OmegaT.

 OmegaT is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 OmegaT is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
 **************************************************************************/

package org.omegat.core.data;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.omegat.core.Core;
import org.omegat.core.data.ParseEntry.ParseEntryResult;
import org.omegat.filters2.FilterContext;
import org.omegat.filters2.IFilter;
import org.omegat.filters2.IParseCallback;
import org.omegat.filters2.master.FilterMaster;
import org.omegat.util.Language;
import org.omegat.util.OConsts;
import org.omegat.util.Preferences;
import org.omegat.util.StringUtil;
import org.omegat.util.TMXProp;
import org.omegat.util.TMXReader2;

/**
 * Common utility class for external TMs.
 * 
 * @author Aaron Madlon-Kay
 *
 */
public class ExternalTMFactory {

    private static final List<String> TMX_EXTENSIONS = Arrays.asList(OConsts.TMX_EXTENSION,
            OConsts.TMX_GZ_EXTENSION);

    public static boolean isSupportedFormat(File file) {
        return isTMXFormat(file) || isBilingualFormat(file);
    }

    public static boolean isTMXFormat(File file) {
        String name = file.getName().toLowerCase();
        return TMX_EXTENSIONS.stream().anyMatch(fmt -> name.endsWith(fmt));
    }

    public static boolean isBilingualFormat(File file) {
        FilterMaster fm = Core.getFilterMaster();
        try {
            return fm.isFileSupported(file, true) && fm.isBilingualFile(file);
        } catch (Exception e) {
            return false;
        }
    }

    public static ExternalTMX load(File file) throws Exception {
        ProjectProperties props = Core.getProject().getProjectProperties();
        if (isTMXFormat(file)) {
            return new TMXLoader(file)
                    .setExtTmxLevel2(Preferences.isPreference(Preferences.EXT_TMX_SHOW_LEVEL2))
                    .setUseSlash(Preferences.isPreference(Preferences.EXT_TMX_USE_SLASH))
                    .setDoSegmenting(props.isSentenceSegmentingEnabled())
                    .load(props.getSourceLanguage(), props.getTargetLanguage());
        } else if (isBilingualFormat(file)) {
            return new BifileLoader(file).setRemoveTags(props.isRemoveTags())
                    .setRemoveSpaces(Core.getFilterMaster().getConfig().isRemoveSpacesNonseg())
                    .setDoSegmenting(props.isSentenceSegmentingEnabled())
                    .load(props.getSourceLanguage(), props.getTargetLanguage());
        } else {
            throw new IllegalArgumentException("Unsupported external TM type: " + file.getName());
        }
    }

    public static class TMXLoader {
        private final File file;
        private boolean extTmxLevel2;
        private boolean useSlash;
        private boolean doSegmenting;
    
        public TMXLoader(File file) {
            this.file = file;
        }
    
        public TMXLoader setExtTmxLevel2(boolean extTmxLevel2) {
            this.extTmxLevel2 = extTmxLevel2;
            return this;
        }
    
        public TMXLoader setUseSlash(boolean useSlash) {
            this.useSlash = useSlash;
            return this;
        }

        public TMXLoader setDoSegmenting(boolean doSegmenting) {
            this.doSegmenting = doSegmenting;
            return this;
        }
    
        public ExternalTMX load(Language sourceLang, Language targetLang) throws Exception {
            return new ExternalTMX(file.getName(), loadImpl(file, doSegmenting, sourceLang, targetLang));
        }
    
        private List<PrepareTMXEntry> loadImpl(File file, boolean doSegmenting, Language sourceLang,
                Language targetLang) throws Exception {
            List<PrepareTMXEntry> entries = new ArrayList<>();
    
            TMXReader2.LoadCallback loader = new TMXReader2.LoadCallback() {
                public boolean onEntry(TMXReader2.ParsedTu tu, TMXReader2.ParsedTuv tuvSource,
                        TMXReader2.ParsedTuv tuvTarget, boolean isParagraphSegtype) {
                    if (tuvSource == null) {
                        return false;
                    }
    
                    if (tuvTarget != null) {
                        // add only target Tuv
                        addTuv(tu, tuvSource, tuvTarget, isParagraphSegtype);
                    } else {
                        // add all non-source Tuv
                        for (int i = 0; i < tu.tuvs.size(); i++) {
                            if (tu.tuvs.get(i) != tuvSource) {
                                addTuv(tu, tuvSource, tu.tuvs.get(i), isParagraphSegtype);
                            }
                        }
                    }
                    return true;
                }
    
                private void addTuv(TMXReader2.ParsedTu tu, TMXReader2.ParsedTuv tuvSource,
                        TMXReader2.ParsedTuv tuvTarget, boolean isParagraphSegtype) {
                    String changer = StringUtil.nvl(tuvTarget.changeid, tuvTarget.creationid, tu.changeid,
                            tu.creationid);
                    String creator = StringUtil.nvl(tuvTarget.creationid, tu.creationid);
                    long changed = StringUtil.nvlLong(tuvTarget.changedate, tuvTarget.creationdate,
                            tu.changedate, tu.creationdate);
                    long created = StringUtil.nvlLong(tuvTarget.creationdate, tu.creationdate);
    
                    List<String> sources = new ArrayList<String>();
                    List<String> targets = new ArrayList<String>();
                    Core.getSegmenter().segmentEntries(doSegmenting && isParagraphSegtype, sourceLang,
                            tuvSource.text, targetLang, tuvTarget.text, sources, targets);
    
                    for (int i = 0; i < sources.size(); i++) {
                        PrepareTMXEntry te = new PrepareTMXEntry();
                        te.source = sources.get(i);
                        te.translation = targets.get(i);
                        te.changer = changer;
                        te.changeDate = changed;
                        te.creator = creator;
                        te.creationDate = created;
                        te.note = tu.note;
                        te.otherProperties = tu.props;
                        entries.add(te);
                    }
                }
            };

            TMXReader2 reader = new TMXReader2();
            reader.readTMX(file, sourceLang, targetLang, doSegmenting, false, extTmxLevel2, useSlash, loader);
    
            return entries;
        }
    }

    public static class BifileLoader {
        private static final ParseEntryResult THROWAWAY = new ParseEntryResult();
        private final File file;
        private boolean removeTags;
        private boolean removeSpaces;
        private boolean doSegmenting;

        public BifileLoader(File file) {
            this.file = file;
        }

        public BifileLoader setRemoveTags(boolean removeTags) {
            this.removeTags = removeTags;
            return this;
        }

        public BifileLoader setRemoveSpaces(boolean removeSpaces) {
            this.removeSpaces = removeSpaces;
            return this;
        }

        public BifileLoader setDoSegmenting(boolean doSegmenting) {
            this.doSegmenting = doSegmenting;
            return this;
        }

        public ExternalTMX load(Language sourceLang, Language targetLang) throws Exception {
            return new ExternalTMX(file.getName(), loadImpl(sourceLang, targetLang));
        }

        private List<PrepareTMXEntry> loadImpl(Language sourceLang, Language targetLang) throws Exception {
            final List<PrepareTMXEntry> entries = new ArrayList<>();
            Core.getFilterMaster().loadFile(file.getPath(),
                    new FilterContext(sourceLang, targetLang, true).setRemoveAllTags(removeTags),
                    new IParseCallback() {
                        @Override
                        public void linkPrevNextSegments() {
                        }

                        @Override
                        public void addEntry(String id, String source, String translation, boolean isFuzzy,
                                String comment, IFilter filter) {
                            process(source, translation, id, comment, null, null);
                        }

                        @Override
                        public void addEntry(String id, String source, String translation, boolean isFuzzy,
                                String comment, String path, IFilter filter,
                                List<ProtectedPart> protectedParts) {
                            process(source, translation, id, comment, path, null);
                        }

                        @Override
                        public void addEntryWithProperties(String id, String source, String translation,
                                boolean isFuzzy, String[] props, String path, IFilter filter,
                                List<ProtectedPart> protectedParts) {
                            process(source, translation, id, null, null, props);

                        }

                        private void process(String source, String target, String id, String comment,
                                String path, String[] props) {
                            if (source == null || target == null) {
                                return;
                            }
                            source = StringUtil.normalizeUnicode(
                                    ParseEntry.stripSomeChars(source, THROWAWAY, removeTags, removeSpaces));
                            target = StringUtil.normalizeUnicode(
                                    ParseEntry.stripSomeChars(target, THROWAWAY, removeTags, removeSpaces));

                            List<String> sources = new ArrayList<>();
                            List<String> targets = new ArrayList<>();
                            Core.getSegmenter().segmentEntries(doSegmenting, sourceLang,
                                    source, targetLang, target, sources, targets);

                            if (sources.size() == targets.size()) {
                                for (int i = 0; i < sources.size(); i++) {
                                    addImpl(sources.get(i), targets.get(i), id, comment, path, props);
                                }
                            } else {
                                addImpl(source, target, id, comment, path, props);
                            }
                        }

                        private void addImpl(String source, String target, String id, String comment,
                                String path, String[] props) {
                            if (!source.trim().isEmpty()) {
                                PrepareTMXEntry entry = new PrepareTMXEntry();
                                entry.source = source;
                                entry.translation = target;
                                entry.note = comment;
                                if (props != null) {
                                    List<TMXProp> tmxProps = propsToList(props);
                                    if (id != null) {
                                        tmxProps.add(new TMXProp("id", id));
                                    }
                                    if (path != null) {
                                        tmxProps.add(new TMXProp("path", path));
                                    }
                                    entry.otherProperties = tmxProps;
                                }
                                entries.add(entry);
                            }
                        }
                    });
            return entries;
        }

        private List<TMXProp> propsToList(String[] props) {
            List<TMXProp> result = new ArrayList<>(props.length / 2);
            for (int i = 0; i < props.length; i++) {
                result.add(new TMXProp(props[i], props[++i]));
            }
            return result;
        }
    }
}