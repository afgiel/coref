package cs224n.corefsystems;


import java.util.Collection;
import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;


import cs224n.coref.*;
import cs224n.util.Pair;
import cs224n.util.CounterMap;

public class RuleBased implements CoreferenceSystem {

  HashMap<Mention, ClusteredMention> mentionsClusterMap;

  HashMap<String, List<String>> headMatches;
  HashSet<String> masculine;
  HashSet<String> feminine;
  HashSet<String> firstPerson;
  HashSet<String> secondPerson; 

	@Override
	public void train(Collection<Pair<Document, List<Entity>>> trainingData) {
    // This is rule-based and thus no training performed
	}

	@Override
	public List<ClusteredMention> runCoreference(Document doc) {
	  mentionsClusterMap = new HashMap<Mention, ClusteredMention>();

    // Guarantees first condition of the sieve pass invariant
    markAllSingleton(doc);
    /* Begin passing through mentions using rules with decreasingly less precision.
     * Each pass must maintain the following invariant:
     *    - All mentions are assigned to a cluster. 
     *    - The mentionsClusterMap is updated so that all cluster assignments are accurate map.
     */
    clusterByExactMatch(doc);
    //clusterByHeadWordMatch(doc);
    clusterByHeadWordLemmaMatch(doc);
    //clusterByProperNounWordMatches(doc);
    clusterPronouns(doc);

    return mentionsMapToClusterList();
  }

  private void markAllSingleton(Document doc) {
    for (Mention m : doc.getMentions()) {
      mentionsClusterMap.put(m, m.markSingleton()); 
    }
  }


  /**
   * Clusters mentions if mention string is an exact match.
   * Does not do this if the word is a pronoun.
   */
  private void clusterByExactMatch(Document doc) {
    HashMap<String, Entity> mentionStringMap = new HashMap<String, Entity>();
    for (Mention m : doc.getMentions()) {
      String mentionString = m.gloss().toLowerCase();
      if (!Pronoun.isSomePronoun(mentionString)) {
        if (mentionStringMap.containsKey(mentionString)) {
          Entity e = mentionStringMap.get(mentionString);
          mergeClusters(mentionsClusterMap.get(m).entity, e);
        } else {
          mentionStringMap.put(mentionString, mentionsClusterMap.get(m).entity);
        }
      }
    }
  }

  /**
   * Clusters mentions if head words are an exact match.
   * Does not do this if the word is a pronoun.
   */
  private void clusterByHeadWordMatch(Document doc) {
    HashMap<String, Entity> headWordMap = new HashMap<String, Entity>();
    for (Mention m : doc.getMentions()) {
      if (!Pronoun.isSomePronoun(m.gloss())) {
        String headWord = m.headWord();
        if (headWordMap.containsKey(headWord)) {
          Entity e = headWordMap.get(headWord);
          mergeClusters(mentionsClusterMap.get(m).entity, e);
        } else {
          headWordMap.put(headWord, mentionsClusterMap.get(m).entity);
        }
      }
    }
  }

  /**
   * Clusters mentions if head word lemmas are an exact match.
   * Does not do this if the word is a pronoun.
   */
  private void clusterByHeadWordLemmaMatch(Document doc) {
    HashMap<String, Entity> headWordLemmaMap = new HashMap<String, Entity>();
    for (Mention m : doc.getMentions()) {
      if (!Pronoun.isSomePronoun(m.gloss())) {
        String lemma = m.sentence.lemmas.get(m.headWordIndex);
        if (headWordLemmaMap.containsKey(lemma)) {
          Entity e = headWordLemmaMap.get(lemma);
          mergeClusters(mentionsClusterMap.get(m).entity, e);
        } else {
          headWordLemmaMap.put(lemma, mentionsClusterMap.get(m).entity);
        }
      }
    }
  }

  private void clusterByProperNounWordMatches(Document doc) {
    HashMap<Pair<String, String>, List<Mention>> properNounsMap = new HashMap<Pair<String, String>, List<Mention>>();
    for (Mention m : doc.getMentions()) {
      Sentence s = m.sentence; 
      for (int i = m.beginIndexInclusive; i < m.endIndexExclusive; i++) {
        Sentence.Token t = s.tokens.get(i);
        String w = s.words.get(i);
        String ner = s.nerTags.get(i);
        if (t.isProperNoun()) {
          Pair<String, String> wordNERPair = new Pair<String, String>(w, ner);
          if (properNounsMap.containsKey(wordNERPair)) {
            properNounsMap.get(wordNERPair).add(m);
          } else {
            properNounsMap.put(wordNERPair, new ArrayList<Mention>());
            properNounsMap.get(wordNERPair).add(m);
          }
        }
      }
    }
    for (Pair<String, String> wordNERPair : properNounsMap.keySet()) {
      List<Mention> toMergeList = properNounsMap.get(wordNERPair);
      if (toMergeList.size() > 1) {
        List<Entity> entities = new ArrayList<Entity>(); 
        for (Mention m : toMergeList) {
          entities.add(mentionsClusterMap.get(m).entity);
        }
        mergeClusters(entities);
      }
    }
  }

  private void clusterPronouns(Document doc) {
    for (Mention m : doc.getMentions()) {
      if (Pronoun.isSomePronoun(m.gloss())) {
        if (mentionsClusterMap.get(m).entity.mentions.size() == 1) {
          for (int i = doc.indexOfMention(m)-1; i >= 0; i--) {
            Mention neighborMention = doc.getMentions().get(i);
            if (Util.isAntecedent(m, neighborMention)) {
              mergeClusters(mentionsClusterMap.get(m).entity, mentionsClusterMap.get(neighborMention).entity);
              break;
            }
          }
        }
      }
    }
  }


  private void mergeClusters(Entity mergingEntity, Entity fixedEntity) {
    if (mergingEntity != fixedEntity) {
      for (Mention m : mergingEntity.mentions) {
        m.removeCoreference();
        mentionsClusterMap.put(m, m.markCoreferent(fixedEntity));
      }
    }
  }

  private void mergeClusters(List<Entity> entities) {
    Entity fixedEntity = entities.get(0); 
    for (Entity e : entities) {
      if (e != fixedEntity) {
        for ( Mention m : e.mentions) {
          m.removeCoreference();
          mentionsClusterMap.put(m, m.markCoreferent(fixedEntity));
        }
      }
    }
  }

  private List<ClusteredMention> mentionsMapToClusterList() {
    List<ClusteredMention> clusteredMentionsList = new ArrayList<ClusteredMention>();
    clusteredMentionsList.addAll(mentionsClusterMap.values());
    return clusteredMentionsList;
  }

}
