package com.jetbrains.python.psi.types;

import com.google.common.collect.ImmutableSet;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.PyDynamicMember;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyImportedModule;
import com.jetbrains.python.psi.impl.PyQualifiedName;
import com.jetbrains.python.psi.impl.ResolveResultList;
import com.jetbrains.python.psi.resolve.*;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.jetbrains.python.psi.PyUtil.inSameFile;

/**
 * @author yole
 */
public class PyModuleType implements PyType { // Modules don't descend from object
  @NotNull private final PyFile myModule;
  @Nullable private final PyImportedModule myImportedModule;

  protected static ImmutableSet<String> ourPossibleFields = ImmutableSet.of("__name__", "__file__", "__path__", "__doc__", "__dict__");

  public PyModuleType(@NotNull PyFile source) {
    this(source, null);
  }
  
  public PyModuleType(@NotNull PyFile source, @Nullable PyImportedModule importedModule) {
    myModule = source;
    myImportedModule = importedModule;
  }

  @NotNull
  public PyFile getModule() {
    return myModule;
  }

  @Nullable
  @Override
  public List<? extends RatedResolveResult> resolveMember(final String name,
                                                          @Nullable PyExpression location,
                                                          AccessDirection direction,
                                                          PyResolveContext resolveContext) {
    for (PyModuleMembersProvider provider : Extensions.getExtensions(PyModuleMembersProvider.EP_NAME)) {
      final PsiElement element = provider.resolveMember(myModule, name);
      if (element != null) {
        return ResolveResultList.to(element);
      }
    }
    final PsiElement attribute = myModule.getElementNamed(name);
    if (attribute != null) {
      return ResolveResultList.to(attribute);
    }
    if (PyUtil.isPackage(myModule)) {
      final List<PyImportElement> importElements = new ArrayList<PyImportElement>();
      if (myImportedModule != null && (location == null || !inSameFile(location, myImportedModule))) {
        final PyImportElement importElement = myImportedModule.getImportElement();
        if (importElement != null) {
          importElements.add(importElement);
        }
      }
      else if (location != null) {
        final ScopeOwner owner = ScopeUtil.getScopeOwner(location);
        if (owner != null) {
          importElements.addAll(getVisibleImports(owner));
        }
        if (!inSameFile(location, myModule)) {
          importElements.addAll(myModule.getImportTargets());
        }
      }
      final List<? extends RatedResolveResult> implicitMembers = resolveImplicitPackageMember(name, importElements);
      if (implicitMembers != null) {
        return implicitMembers;
      }
    }
    return null;
  }

  @Nullable
  private List<? extends RatedResolveResult> resolveImplicitPackageMember(@NotNull String name,
                                                                          @NotNull List<PyImportElement> importElements) {
    final PyQualifiedName packageQName = ResolveImportUtil.findCanonicalImportPath(myModule, null);
    if (packageQName != null) {
      final PyQualifiedName resolvingQName = packageQName.append(name);
      for (PyImportElement importElement : importElements) {
        for (PyQualifiedName qName : getCanonicalImportedQNames(importElement)) {
          if (qName.matchesPrefix(resolvingQName)) {
            final PsiElement subModule = ResolveImportUtil.resolveChild(myModule, name, myModule, false, true);
            if (subModule != null) {
              final ResolveResultList results = new ResolveResultList();
              results.add(new ImportedResolveResult(subModule, RatedResolveResult.RATE_NORMAL,
                                                    Collections.<PsiElement>singletonList(importElement)));
              return results;
            }
          }
        }
      }
    }
    return null;
  }

  @NotNull
  private List<PyQualifiedName> getCanonicalImportedQNames(@NotNull PyImportElement element) {
    final List<PyQualifiedName> importedQNames = new ArrayList<PyQualifiedName>();
    final PyStatement stmt = element.getContainingImportStatement();
    if (stmt instanceof PyFromImportStatement) {
      final PyFromImportStatement fromImportStatement = (PyFromImportStatement)stmt;
      final PyQualifiedName importedQName = fromImportStatement.getImportSourceQName();
      final String visibleName = element.getVisibleName();
      if (importedQName != null) {
        importedQNames.add(importedQName);
        final PyQualifiedName implicitSubModuleQName = importedQName.append(visibleName);
        if (implicitSubModuleQName != null) {
          importedQNames.add(implicitSubModuleQName);
        }
      }
    }
    else if (stmt instanceof PyImportStatement) {
      final PyQualifiedName importedQName = element.getImportedQName();
      if (importedQName != null) {
        importedQNames.add(importedQName);
      }
    }
    if (!ResolveImportUtil.isAbsoluteImportEnabledFor(element)) {
      PsiFile file = element.getContainingFile();
      if (file != null) {
        file = file.getOriginalFile();
      }
      final PyQualifiedName absoluteQName = ResolveImportUtil.findShortestImportableQName(file);
      if (file != null && absoluteQName != null) {
        final PyQualifiedName prefixQName = PyUtil.isPackage(file) ? absoluteQName : absoluteQName.removeLastComponent();
        if (prefixQName.getComponentCount() > 0) {
          final List<PyQualifiedName> results = new ArrayList<PyQualifiedName>();
          results.addAll(importedQNames);
          for (PyQualifiedName qName : importedQNames) {
            final List<String> components = new ArrayList<String>();
            components.addAll(prefixQName.getComponents());
            components.addAll(qName.getComponents());
            results.add(PyQualifiedName.fromComponents(components));
          }
          return results;
        }
      }
    }
    return importedQNames;
  }

  @NotNull
  private static List<PyImportElement> getVisibleImports(@NotNull ScopeOwner owner) {
    final List<PyImportElement> visibleImports = new ArrayList<PyImportElement>();
    PyResolveUtil.scopeCrawlUp(new PsiScopeProcessor() {
      @Override
      public boolean execute(@NotNull PsiElement element, ResolveState state) {
        if (element instanceof PyImportElement) {
          visibleImports.add((PyImportElement)element);
        }
        return true;
      }

      @Nullable
      @Override
      public <T> T getHint(@NotNull Key<T> hintKey) {
        return null;
      }

      @Override
      public void handleEvent(Event event, @Nullable Object associated) {
      }
    }, owner, null, null);
    return visibleImports;
  }

  /**
   * @param directory the module directory
   *
   * @return a list of submodules of the specified module directory, either files or dirs, for easier naming; may contain file names
   *         not suitable for import.
   */
  @NotNull
  public static List<PsiFileSystemItem> getSubmodulesList(final PsiDirectory directory) {
    List<PsiFileSystemItem> result = new ArrayList<PsiFileSystemItem>();

    if (directory != null) { // just in case
      // file modules
      for (PsiFile f : directory.getFiles()) {
        final String filename = f.getName();
        // if we have a binary module, we'll most likely also have a stub for it in site-packages
        if ((f instanceof PyFile && !filename.equals(PyNames.INIT_DOT_PY)) || isBinaryModule(filename)) {
          result.add(f);
        }
      }
      // dir modules
      for (PsiDirectory dir : directory.getSubdirectories()) {
        if (dir.findFile(PyNames.INIT_DOT_PY) instanceof PyFile) result.add(dir);
      }
    }
    return result;
  }

  private static boolean isBinaryModule(String filename) {
    final String ext = FileUtil.getExtension(filename);
    if (SystemInfo.isWindows) {
      return "pyd".equalsIgnoreCase(ext);
    }
    else {
      return "so".equals(ext);
    }
  }

  public Object[] getCompletionVariants(String completionPrefix, PyExpression location, ProcessingContext context) {
    Set<String> names_already = context.get(CTX_NAMES);
    List<Object> result = new ArrayList<Object>();

    PointInImport point = ResolveImportUtil.getPointInImport(location);
    for (PyModuleMembersProvider provider : Extensions.getExtensions(PyModuleMembersProvider.EP_NAME)) {
      for (PyDynamicMember member : provider.getMembers(myModule, point)) {
        final String name = member.getName();
        result.add(LookupElementBuilder.create(name).withIcon(member.getIcon()).withTypeText(member.getShortType()));
      }
    }

    if (point == PointInImport.NONE || point == PointInImport.AS_NAME) { // when not imported from, add regular attributes
      final CompletionVariantsProcessor processor = new CompletionVariantsProcessor(location, new Condition<PsiElement>() {
        @Override
        public boolean value(PsiElement psiElement) {
          return !(psiElement instanceof PyImportElement) ||
                 PsiTreeUtil.getParentOfType(psiElement, PyImportStatementBase.class) instanceof PyFromImportStatement;
        }
      }, new PyUtil.UnderscoreFilter(0));
      processor.setPlainNamesOnly(point  == PointInImport.AS_NAME); // no parens after imported function names
      myModule.processDeclarations(processor, ResolveState.initial(), null, location);
      if (names_already != null) {
        for (LookupElement le : processor.getResultList()) {
          String name = le.getLookupString();
          if (!names_already.contains(name)) {
            result.add(le);
            names_already.add(name);
          }
        }
      }
      else {
        result.addAll(processor.getResultList());
      }
    }
    if (PyUtil.isPackage(myModule)) { // our module is a dir, not a single file
      if (point == PointInImport.AS_MODULE || point == PointInImport.AS_NAME) { // when imported from somehow, add submodules
        result.addAll(getSubModuleVariants(myModule.getContainingDirectory(), location, names_already));
      }
      else {
        addImportedSubmodules(location, names_already, result);
      }
    }
    return result.toArray();
  }

  private void addImportedSubmodules(PyExpression location, Set<String> exiatingNames, List<Object> result) {
    PsiFile file = location.getContainingFile();
    if (file instanceof PyFile) {
      PyFile pyFile = (PyFile)file;
      PsiElement moduleBase = PyUtil.isPackage(myModule) ? myModule.getContainingDirectory() : myModule;
      for (PyImportElement importElement : pyFile.getImportTargets()) {
        PsiElement target = ResolveImportUtil.resolveImportElement(importElement);
        if (target != null && PsiTreeUtil.isAncestor(moduleBase, target, true)) {
          if (target instanceof PsiFile && PyUtil.isPackage((PsiFile)target)) {
            continue;
          }
          LookupElement element = null;
          if (target instanceof PsiFileSystemItem) {
            element = buildFileLookupElement((PsiFileSystemItem) target, exiatingNames);
          }
          else if (target instanceof PsiNamedElement) {
            element = LookupElementBuilder.createWithIcon((PsiNamedElement)target);
          }
          if (element != null) {
            result.add(element);
          }
        }
      }
    }
  }

  public static List<LookupElement> getSubModuleVariants(final PsiDirectory directory,
                                                         PsiElement location,
                                                         Set<String> names_already) {
    List<LookupElement> result = new ArrayList<LookupElement>();
    for (PsiFileSystemItem item : getSubmodulesList(directory)) {
      if (item != location.getContainingFile().getOriginalFile()) {
        LookupElement lookupElement = buildFileLookupElement(item, names_already);
        if (lookupElement != null) {
          result.add(lookupElement);
        }
      }
    }
    return result;
  }

  @Nullable
  public static LookupElementBuilder buildFileLookupElement(PsiFileSystemItem item, @Nullable Set<String> existingNames) {
    String s = FileUtil.getNameWithoutExtension(item.getName());
    if (!PyNames.isIdentifier(s)) return null;
    if (existingNames != null) {
      if (existingNames.contains(s)) return null;
      else existingNames.add(s);
    }
    return LookupElementBuilder.create(item, s)
      .withTypeText(getPresentablePath((PsiDirectory)item.getParent()))
      .withPresentableText(s)
      .withIcon(item.getIcon(0));
  }

  private static String getPresentablePath(PsiDirectory directory) {
    if (directory == null) {
      return "";
    }
    final String path = directory.getVirtualFile().getPath();
    if (path.contains(PythonSdkType.SKELETON_DIR_NAME)) {
      return "<built-in>";
    }
    return FileUtil.toSystemDependentName(path);
  }

  public String getName() {
    return myModule.getName();
  }

  @Override
  public boolean isBuiltin(TypeEvalContext context) {
    return true;
  }

  @Override
  public void assertValid(String message) {
    if (myModule != null && !myModule.isValid()) {
      throw new PsiInvalidElementAccessException(myModule, myModule.getClass().toString() + ": " + message);
    }
  }

  @NotNull
  public static Set<String> getPossibleInstanceMembers() {
    return ourPossibleFields;
  }

}
