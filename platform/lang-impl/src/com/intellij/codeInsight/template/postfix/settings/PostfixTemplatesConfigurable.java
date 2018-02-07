// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template.postfix.settings;

import com.intellij.application.options.editor.EditorOptionsProvider;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.template.impl.LiveTemplateCompletionContributor;
import com.intellij.codeInsight.template.impl.TemplateSettings;
import com.intellij.codeInsight.template.postfix.templates.LanguagePostfixTemplate;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider;
import com.intellij.codeInsight.template.postfix.templates.editable.PostfixEditableTemplateProvider;
import com.intellij.lang.LanguageExtensionPoint;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.GuiUtils;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.util.Comparator;
import java.util.Set;

public class PostfixTemplatesConfigurable implements SearchableConfigurable, EditorOptionsProvider, Configurable.NoScroll {
  public static final Comparator<PostfixTemplate> TEMPLATE_COMPARATOR = Comparator.comparing(PostfixTemplate::getKey);

  @Nullable
  private PostfixTemplatesCheckboxTree myCheckboxTree;

  @NotNull
  private final PostfixTemplatesSettings myTemplatesSettings;

  @Nullable
  private PostfixDescriptionPanel myInnerPostfixDescriptionPanel;

  @NotNull
  private final MultiMap<String, PostfixTemplate> myTemplates = MultiMap.create();
  private final MultiMap<String, PostfixEditableTemplateProvider> myLangToEditableTemplateProviders = MultiMap.create();

  private JComponent myPanel;
  private JBCheckBox myCompletionEnabledCheckbox;
  private JBCheckBox myPostfixTemplatesEnabled;
  private JPanel myTemplatesTreeContainer;
  private ComboBox<String> myShortcutComboBox;
  private JPanel myDescriptionPanel;

  private static final String SPACE = CodeInsightBundle.message("template.shortcut.space");
  private static final String TAB = CodeInsightBundle.message("template.shortcut.tab");
  private static final String ENTER = CodeInsightBundle.message("template.shortcut.enter");

  public PostfixTemplatesConfigurable() {
    myTemplatesSettings = PostfixTemplatesSettings.getInstance();
    LanguageExtensionPoint[] extensions = new ExtensionPointName<LanguageExtensionPoint>(LanguagePostfixTemplate.EP_NAME).getExtensions();
    for (LanguageExtensionPoint extension : extensions) {
      PostfixTemplateProvider templateProvider = (PostfixTemplateProvider)extension.getInstance();
      if (templateProvider instanceof PostfixEditableTemplateProvider) {
        myLangToEditableTemplateProviders.putValue(extension.getKey(), (PostfixEditableTemplateProvider)templateProvider);
      }
      Set<PostfixTemplate> templates = templateProvider.getTemplates();
      if (!templates.isEmpty()) {
        myTemplates.putValues(extension.getKey(), ContainerUtil.sorted(templates, TEMPLATE_COMPARATOR));
      }
    }

    myPostfixTemplatesEnabled.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        updateComponents();
      }
    });
    myShortcutComboBox.addItem(TAB);
    myShortcutComboBox.addItem(SPACE);
    myShortcutComboBox.addItem(ENTER);
    myDescriptionPanel.setLayout(new BorderLayout());
  }

  private void createTree() {
    myCheckboxTree = new PostfixTemplatesCheckboxTree() {
      @Override
      protected void selectionChanged() {
        resetDescriptionPanel();
      }
    };

    JPanel panel = new JPanel(new BorderLayout());
    ToolbarDecorator decorator = ToolbarDecorator.createDecorator(myCheckboxTree);
    decorator.setAddActionUpdater(e -> !myLangToEditableTemplateProviders.isEmpty());
    decorator.setAddAction(e -> {
      // todo: add list of possible template types from myLangToEditableTemplateProviders
    });
    decorator.setEditAction(e -> myCheckboxTree.editSelectedTemplate());
    decorator.setEditActionUpdater(e -> myCheckboxTree.canUpdateSelectedTemplate());
    decorator.setRemoveAction(e -> myCheckboxTree.removeSelectedTemplates());
    decorator.setRemoveActionUpdater(e -> myCheckboxTree.canRemoveSelectedTemplates());
    panel.add(decorator.createPanel());

    myTemplatesTreeContainer.setLayout(new BorderLayout());
    myTemplatesTreeContainer.add(panel);
  }

  private void resetDescriptionPanel() {
    if (null != myCheckboxTree && null != myInnerPostfixDescriptionPanel) {
      myInnerPostfixDescriptionPanel.reset(PostfixTemplateMetaData.createMetaData(myCheckboxTree.getTemplate()));
      myInnerPostfixDescriptionPanel.resetHeights(myDescriptionPanel.getWidth());
    }
  }

  @NotNull
  @Override
  public String getId() {
    return "reference.settingsdialog.IDE.editor.postfix.templates";
  }

  @Nullable
  @Override
  public String getHelpTopic() {
    return getId();
  }

  @Nls
  @Override
  public String getDisplayName() {
    return "Postfix Completion";
  }

  @Nullable
  public PostfixTemplatesCheckboxTree getTemplatesTree() {
    return myCheckboxTree;
  }

  @NotNull
  @Override
  public JComponent createComponent() {
    GuiUtils.replaceJSplitPaneWithIDEASplitter(myPanel);
    if (null == myInnerPostfixDescriptionPanel) {
      myInnerPostfixDescriptionPanel = new PostfixDescriptionPanel();
      myDescriptionPanel.add(myInnerPostfixDescriptionPanel.getComponent());
    }
    if (null == myCheckboxTree) {
      createTree();
    }

    return myPanel;
  }

  @Override
  public void apply() throws ConfigurationException {
    if (myCheckboxTree != null) {
      myTemplatesSettings.setLangDisabledTemplates(myCheckboxTree.getDisabledTemplatesState());
      myTemplatesSettings.setPostfixTemplatesEnabled(myPostfixTemplatesEnabled.isSelected());
      myTemplatesSettings.setTemplatesCompletionEnabled(myCompletionEnabledCheckbox.isSelected());
      myTemplatesSettings.setShortcut(stringToShortcut((String)myShortcutComboBox.getSelectedItem()));

      MultiMap<String, PostfixTemplate> state = myCheckboxTree.getEditableTemplates();
      for (PostfixEditableTemplateProvider provider : myLangToEditableTemplateProviders.values()) {
        PostfixTemplateStorage.getInstance().setTemplates(provider, state.get(provider.getId()));
      }
    }
  }

  @Override
  public void reset() {
    if (myCheckboxTree != null) {
      myCheckboxTree.initTree(myTemplates);
      myCheckboxTree.setDisabledTemplatesState(myTemplatesSettings.getLangDisabledTemplates());
      myPostfixTemplatesEnabled.setSelected(myTemplatesSettings.isPostfixTemplatesEnabled());
      myCompletionEnabledCheckbox.setSelected(myTemplatesSettings.isTemplatesCompletionEnabled());
      myShortcutComboBox.setSelectedItem(shortcutToString((char)myTemplatesSettings.getShortcut()));
      resetDescriptionPanel();
      updateComponents();
    }
  }

  @Override
  public boolean isModified() {
    if (myCheckboxTree == null) {
      return false;
    }
    if (myPostfixTemplatesEnabled.isSelected() != myTemplatesSettings.isPostfixTemplatesEnabled() ||
        myCompletionEnabledCheckbox.isSelected() != myTemplatesSettings.isTemplatesCompletionEnabled() ||
        stringToShortcut((String)myShortcutComboBox.getSelectedItem()) != myTemplatesSettings.getShortcut() ||
        !myCheckboxTree.getDisabledTemplatesState().equals(myTemplatesSettings.getLangDisabledTemplates())) {
      return true;
    }

    MultiMap<String, PostfixTemplate> state = myCheckboxTree.getEditableTemplates();
    for (PostfixEditableTemplateProvider provider : myLangToEditableTemplateProviders.values()) {
      if (!PostfixTemplateStorage.getInstance().getTemplates(provider).equals(state.get(provider.getId()))) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void disposeUIResources() {
    if (myInnerPostfixDescriptionPanel != null) {
      Disposer.dispose(myInnerPostfixDescriptionPanel);
    }
    myTemplates.clear();
    myCheckboxTree = null;
  }

  private void updateComponents() {
    boolean pluginEnabled = myPostfixTemplatesEnabled.isSelected();
    myCompletionEnabledCheckbox.setVisible(!LiveTemplateCompletionContributor.shouldShowAllTemplates());
    myCompletionEnabledCheckbox.setEnabled(pluginEnabled);
    myShortcutComboBox.setEnabled(pluginEnabled);
    if (myCheckboxTree != null) {
      myCheckboxTree.setEnabled(pluginEnabled);
    }
  }

  private static char stringToShortcut(@Nullable String string) {
    if (SPACE.equals(string)) {
      return TemplateSettings.SPACE_CHAR;
    }
    else if (ENTER.equals(string)) {
      return TemplateSettings.ENTER_CHAR;
    }
    return TemplateSettings.TAB_CHAR;
  }

  private static String shortcutToString(char shortcut) {
    if (shortcut == TemplateSettings.SPACE_CHAR) {
      return SPACE;
    }
    if (shortcut == TemplateSettings.ENTER_CHAR) {
      return ENTER;
    }
    return TAB;
  }
}
