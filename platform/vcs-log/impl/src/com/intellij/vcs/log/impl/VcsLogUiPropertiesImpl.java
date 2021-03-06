// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.impl;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.vcs.log.graph.PermanentGraph;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.vcs.log.ui.table.GraphTableModel.*;

/**
 * Stores UI configuration based on user activity and preferences.
 */
public abstract class VcsLogUiPropertiesImpl<S extends VcsLogUiPropertiesImpl.State>
  implements PersistentStateComponent<S>, MainVcsLogUiProperties {
  private static final Set<VcsLogUiProperties.VcsLogUiProperty> SUPPORTED_PROPERTIES =
    ContainerUtil.newHashSet(CommonUiProperties.SHOW_DETAILS,
                             MainVcsLogUiProperties.SHOW_LONG_EDGES,
                             MainVcsLogUiProperties.BEK_SORT_TYPE,
                             CommonUiProperties.SHOW_ROOT_NAMES,
                             MainVcsLogUiProperties.SHOW_ONLY_AFFECTED_CHANGES,
                             MainVcsLogUiProperties.TEXT_FILTER_MATCH_CASE,
                             MainVcsLogUiProperties.TEXT_FILTER_REGEX,
                             CommonUiProperties.COLUMN_ORDER);
  private final Set<PropertiesChangeListener> myListeners = new LinkedHashSet<>();
  @NotNull private final VcsLogApplicationSettings myAppSettings;

  public VcsLogUiPropertiesImpl(@NotNull VcsLogApplicationSettings appSettings) {
    myAppSettings = appSettings;
  }

  public static class State {
    public boolean SHOW_DETAILS_IN_CHANGES = true;
    public boolean LONG_EDGES_VISIBLE = false;
    public int BEK_SORT_TYPE = 0;
    public boolean SHOW_ROOT_NAMES = false;
    public boolean SHOW_ONLY_AFFECTED_CHANGES = false;
    public Map<String, Boolean> HIGHLIGHTERS = new TreeMap<>();
    public Map<String, List<String>> FILTERS = new TreeMap<>();
    public TextFilterSettings TEXT_FILTER_SETTINGS = new TextFilterSettings();
    public Map<Integer, Integer> COLUMN_WIDTH = new HashMap<>();
    public List<Integer> COLUMN_ORDER = new ArrayList<>();
  }

  @NotNull
  @Override
  public abstract S getState();

  @SuppressWarnings("unchecked")
  @NotNull
  @Override
  public <T> T get(@NotNull VcsLogUiProperties.VcsLogUiProperty<T> property) {
    if (myAppSettings.exists(property)) {
      return myAppSettings.get(property);
    }

    if (CommonUiProperties.SHOW_DETAILS.equals(property)) {
      return (T)Boolean.valueOf(getState().SHOW_DETAILS_IN_CHANGES);
    }
    else if (SHOW_LONG_EDGES.equals(property)) {
      return (T)Boolean.valueOf(getState().LONG_EDGES_VISIBLE);
    }
    else if (CommonUiProperties.SHOW_ROOT_NAMES.equals(property)) {
      return (T)Boolean.valueOf(getState().SHOW_ROOT_NAMES);
    }
    else if (SHOW_ONLY_AFFECTED_CHANGES.equals(property)) {
      return (T)Boolean.valueOf(getState().SHOW_ONLY_AFFECTED_CHANGES);
    }
    else if (BEK_SORT_TYPE.equals(property)) {
      return (T)PermanentGraph.SortType.values()[getState().BEK_SORT_TYPE];
    }
    else if (TEXT_FILTER_MATCH_CASE.equals(property)) {
      return (T)Boolean.valueOf(getTextFilterSettings().MATCH_CASE);
    }
    else if (TEXT_FILTER_REGEX.equals(property)) {
      return (T)Boolean.valueOf(getTextFilterSettings().REGEX);
    }
    else if (CommonUiProperties.COLUMN_ORDER.equals(property)) {
      List<Integer> order = getState().COLUMN_ORDER;
      if (order == null || order.isEmpty()) {
        order = ContainerUtilRt.newArrayList(ROOT_COLUMN, COMMIT_COLUMN, AUTHOR_COLUMN, DATE_COLUMN);
      }
      return (T)order;
    }
    else if (property instanceof VcsLogHighlighterProperty) {
      Boolean result = getState().HIGHLIGHTERS.get(((VcsLogHighlighterProperty)property).getId());
      if (result == null) return (T)Boolean.TRUE;
      return (T)result;
    }
    else if (property instanceof CommonUiProperties.TableColumnProperty) {
      Integer savedWidth = getState().COLUMN_WIDTH.get(((CommonUiProperties.TableColumnProperty)property).getColumn());
      if (savedWidth == null) return (T)Integer.valueOf(-1);
      return (T)savedWidth;
    }
    throw new UnsupportedOperationException("Property " + property + " does not exist");
  }

  @SuppressWarnings("unchecked")
  @Override
  public <T> void set(@NotNull VcsLogUiProperties.VcsLogUiProperty<T> property, @NotNull T value) {
    if (myAppSettings.exists(property)) {
      myAppSettings.set(property, value);
      return;
    }

    if (CommonUiProperties.SHOW_DETAILS.equals(property)) {
      getState().SHOW_DETAILS_IN_CHANGES = (Boolean)value;
    }
    else if (SHOW_LONG_EDGES.equals(property)) {
      getState().LONG_EDGES_VISIBLE = (Boolean)value;
    }
    else if (CommonUiProperties.SHOW_ROOT_NAMES.equals(property)) {
      getState().SHOW_ROOT_NAMES = (Boolean)value;
    }
    else if (SHOW_ONLY_AFFECTED_CHANGES.equals(property)) {
      getState().SHOW_ONLY_AFFECTED_CHANGES = (Boolean)value;
    }
    else if (BEK_SORT_TYPE.equals(property)) {
      getState().BEK_SORT_TYPE = ((PermanentGraph.SortType)value).ordinal();
    }
    else if (TEXT_FILTER_REGEX.equals(property)) {
      getTextFilterSettings().REGEX = (boolean)(Boolean)value;
    }
    else if (TEXT_FILTER_MATCH_CASE.equals(property)) {
      getTextFilterSettings().MATCH_CASE = (boolean)(Boolean)value;
    }
    else if (CommonUiProperties.COLUMN_ORDER.equals(property)) {
      getState().COLUMN_ORDER = (List<Integer>)value;
    }
    else if (property instanceof VcsLogHighlighterProperty) {
      getState().HIGHLIGHTERS.put(((VcsLogHighlighterProperty)property).getId(), (Boolean)value);
    }
    else if (property instanceof CommonUiProperties.TableColumnProperty) {
      getState().COLUMN_WIDTH.put(((CommonUiProperties.TableColumnProperty)property).getColumn(), (Integer)value);
    }
    else {
      throw new UnsupportedOperationException("Property " + property + " does not exist");
    }
    myListeners.forEach(l -> l.onPropertyChanged(property));
  }

  @Override
  public <T> boolean exists(@NotNull VcsLogUiProperties.VcsLogUiProperty<T> property) {
    if (myAppSettings.exists(property) ||
        SUPPORTED_PROPERTIES.contains(property) ||
        property instanceof VcsLogHighlighterProperty ||
        property instanceof CommonUiProperties.TableColumnProperty) {
      return true;
    }
    return false;
  }

  @NotNull
  private TextFilterSettings getTextFilterSettings() {
    TextFilterSettings settings = getState().TEXT_FILTER_SETTINGS;
    if (settings == null) {
      settings = new TextFilterSettings();
      getState().TEXT_FILTER_SETTINGS = settings;
    }
    return settings;
  }

  @Override
  public void saveFilterValues(@NotNull String filterName, @Nullable List<String> values) {
    if (values != null) {
      getState().FILTERS.put(filterName, values);
    }
    else {
      getState().FILTERS.remove(filterName);
    }
  }

  @Nullable
  @Override
  public List<String> getFilterValues(@NotNull String filterName) {
    return getState().FILTERS.get(filterName);
  }

  @Override
  public void addChangeListener(@NotNull PropertiesChangeListener listener) {
    myListeners.add(listener);
    myAppSettings.addChangeListener(listener);
  }

  @Override
  public void removeChangeListener(@NotNull PropertiesChangeListener listener) {
    myListeners.remove(listener);
    myAppSettings.removeChangeListener(listener);
  }

  public static class TextFilterSettings {
    public boolean REGEX = false;
    public boolean MATCH_CASE = false;
  }
}
