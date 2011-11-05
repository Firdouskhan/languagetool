/* LanguageTool, a natural language style checker 
 * Copyright (C) 2011 Daniel Naber (http://www.danielnaber.de)
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301
 * USA
 */
package org.languagetool.dev.wikipedia;

import org.languagetool.JLanguageTool;
import org.languagetool.Language;
import org.languagetool.TextFilter;
import org.languagetool.rules.RuleMatch;
import org.languagetool.tools.StringTools;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

/**
 * Check a Wikipedia page, fetching the page via the MediaWiki API.
 */
public class WikipediaQuickCheck {

  // disable some rules because of too many false alarms:
  private static final List<String> disabledRuleIds = Arrays.asList("WHITESPACE_RULE", "DE_CASE",
          "UNPAIRED_BRACKETS", "UPPERCASE_SENTENCE_START", "COMMA_PARENTHESIS_WHITESPACE",
          "DE_AGREEMENT", "PFEILE", "BISSTRICH", "AUSLASSUNGSPUNKTE");

  public List<String> getDisabledRuleIds() {
    return disabledRuleIds;
  }

  public WikipediaQuickCheckResult checkPage(URL wikipediaUrl) throws IOException {
    validateWikipediaUrl(wikipediaUrl);
    final String shortLangName;
    final String pageTitle;
    try {
      final int prefixLength = "http://".length();
      shortLangName = wikipediaUrl.toString().substring(prefixLength, prefixLength + 2);
      pageTitle = wikipediaUrl.toString().substring("http://xx.wikipedia.org/wiki/".length());
    } catch (StringIndexOutOfBoundsException e) {
      throw new RuntimeException("URL does not seem to be a valid URL: " + wikipediaUrl.toString(), e);
    }
    final Language lang = Language.getLanguageForShortName(shortLangName);
    if (lang == null) {
      throw new RuntimeException("Language '" + shortLangName + "' is not supported by LanguageTool");
    }
    
    // TODO: remove this restriction
    if (lang != Language.GERMAN) {
      throw new RuntimeException("Sorry, only German is support for now");
    }
      
    final String apiUrl = "http://" + lang.getShortName() + ".wikipedia.org/w/api.php?titles=" 
            + pageTitle + "&action=query&prop=revisions&rvprop=content&format=xml";
    final String completeWikiContent = getContent(new URL(apiUrl));
    final String plainText = getFilteredWikiContent(apiUrl, completeWikiContent);

    final JLanguageTool langTool = getLanguageTool(lang);
    final List<RuleMatch> ruleMatches = langTool.check(plainText);
    return new WikipediaQuickCheckResult(plainText, ruleMatches, lang.getShortName());
  }

  private void validateWikipediaUrl(URL wikipediaUrl) {
    if (!wikipediaUrl.toString().matches("http://..\\.wikipedia\\.org/wiki/.*")) {
      throw new RuntimeException("URL does not seem to be a Wikipedia URL: " + wikipediaUrl);
    }
  }

  private String getFilteredWikiContent(String apiUrl, String completeWikiContent) {
    final int fromPos = completeWikiContent.indexOf("<rev ");
    final int toPos = completeWikiContent.indexOf("</rev>");
    if (fromPos == -1 || toPos == -1) {
      throw new RuntimeException("Sorry, no content found in article at " + apiUrl);
    }
    final String wikiContent = completeWikiContent.substring(completeWikiContent.indexOf(">", fromPos) + 1, toPos);
    //final BlikiWikipediaTextFilter filter = new BlikiWikipediaTextFilter();
    final TextFilter filter = new SwebleWikipediaTextFilter();
    final String plainText = filter.filter(wikiContent).replace("nbsp;", " ");  //TODO
    return plainText;
  }

  private JLanguageTool getLanguageTool(Language lang) throws IOException {
    final JLanguageTool langTool = new JLanguageTool(lang);
    langTool.activateDefaultPatternRules();
    for (String disabledRuleId : disabledRuleIds) {
      langTool.disableRule(disabledRuleId);
    }
    return langTool;
  }

  private String getContent(URL wikipediaUrl) throws IOException {
    final InputStream contentStream = (InputStream) wikipediaUrl.getContent();
    return StringTools.streamToString(contentStream);
  }

  public static void main(String[] args) throws IOException {
    final WikipediaQuickCheck check = new WikipediaQuickCheck();
    //final String url = "http://de.wikipedia.org/wiki/Hof";
    final String url = "http://de.wikipedia.org/wiki/Benutzer_Diskussion:Dnaber";
    final WikipediaQuickCheckResult checkResult = check.checkPage(new URL(url));
    for (RuleMatch ruleMatch : checkResult.getRuleMatches()) {
      System.out.println(ruleMatch.getMessage());
      final String context = StringTools.getContext(ruleMatch.getFromPos(), ruleMatch.getToPos(), checkResult.getText());
      System.out.println(context);
    }
  }

}
