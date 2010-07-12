/*
 * Copyright 2007 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.intellij.plugins.relaxNG.compact.psi.impl;

import org.intellij.plugins.relaxNG.compact.RncTokenTypes;
import org.intellij.plugins.relaxNG.compact.psi.RncNsDecl;

import com.intellij.lang.ASTNode;

/**
 * Created by IntelliJ IDEA.
 * User: sweinreuter
 * Date: 14.08.2007
 */
public class RncNsDeclImpl extends RncDeclImpl implements RncNsDecl {
  public RncNsDeclImpl(ASTNode node) {
    super(node);
  }

  public boolean isDefault() {
    return getNode().findChildByType(RncTokenTypes.KEYWORD_DEFAULT) != null;
  }
}
