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
    headMatches = new HashMap<String, List<String>>(); 
    for (Pair<Document, List<Entity>> trainPair : trainingData) {
      for (Entity entity : trainPair.getSecond()) {
        for (Pair<Mention, Mention> mentionPair : entity.orderedMentionPairs()) {
          String first = mentionPair.getFirst().headWord();
          String second = mentionPair.getSecond().headWord();
          if (headMatches.containsKey(first)) {
            headMatches.get(first).add(second);
          } else {
            List<String> list = new ArrayList<String>();
            list.add(second);
            headMatches.put(first, list);
          }
        }
      }
    }
   masculine = new HashSet<String>(Arrays.asList("Mr.", "he", "his", "him")); 
   feminine = new HashSet<String>(Arrays.asList("Mrs.", "Ms.", "she", "her")); 
   firstPerson = new HashSet<String>(Arrays.asList("I", "me", "mine", "my")); 
   secondPerson = new HashSet<String>(Arrays.asList("you", "your", "yours")); 
	}

	@Override
	public List<ClusteredMention> runCoreference(Document doc) {
	  mentionsClusterMap = new HashMap<Mention, ClusteredMention>();

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
          /*
          hwClusters.put(m.headWord().toLowerCase(), newEntity);
          lClusters.put(m.sentence.lemmas.get(m.headWordIndex), newEntity);
          */
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
            if (isAntecedent(m, neighborMention)) {
              mergeClusters(mentionsClusterMap.get(m).entity, mentionsClusterMap.get(neighborMention).entity);
              break;
            }
          }
        }
      }
    }
  }

  private Boolean isAntecedent(Mention m, Mention neighborMention) {
    if (Pronoun.isSomePronoun(neighborMention.gloss())) {
      if (neighborMention.gloss().equals(m.gloss()) || ( Util.haveGenderAndAreSameGender(m, neighborMention).getSecond() &&
                                                         Util.haveNumberAndAreSameNumber(m, neighborMention).getSecond())) {
        return true;
      }
    } else if (Util.haveGenderAndAreSameGender(m, neighborMention).getSecond() && Util.haveNumberAndAreSameNumber(m, neighborMention).getSecond()) {
      return true;
    }
    return false;
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


/*
    for (Sentence s : doc.sentences) {
      System.out.println(s);
      System.out.println("----");
    }
	  mentionsClusterMap = new HashMap<Mention, ClusteredMention>();
    HashSet<Mention> singles = new HashSet<Mention>();
    HashMap<String, Entity> exactMatchClusters = new HashMap<String, Entity>();
    HashMap<String, Entity> hwClusters = new HashMap<String, Entity>();
    HashMap<String, Entity> lClusters = new HashMap<String, Entity>();


    System.out.println("\n\n\n-----------------------------HERE LIE THE EXPERIMENTS !!!!!!!!!!!!!");
    System.out.println("EXPERIMENTS ARE OVEERRRRR ------------------------\n\n\n");

    List<ClusteredMention> mentionList = new ArrayList<ClusteredMention>();
    mentionList.addAll(mentions.values());
    HashSet<Entity> entities = new HashSet<Entity>();
    for (ClusteredMention cm : mentionList) {
      entities.add(cm.entity);
    }
    for (Entity e : entities) {
      System.out.println(e);
    }
    System.out.println("\n\nDONE\n\n");
    return mentionsMapToClusterList();
	}
*/

}
