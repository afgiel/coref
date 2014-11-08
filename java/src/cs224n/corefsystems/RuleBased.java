package cs224n.corefsystems;

import java.util.Collection;
import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;


import cs224n.coref.ClusteredMention;
import cs224n.coref.Document;
import cs224n.coref.*;
import cs224n.util.Pair;
import cs224n.util.CounterMap;

public class RuleBased implements CoreferenceSystem {

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
    for (Sentence s : doc.sentences) {
      System.out.println(s);
      System.out.println("----");
    }
	  HashMap<Mention, ClusteredMention> mentions = new HashMap<Mention, ClusteredMention>();
    HashSet<Mention> singles = new HashSet<Mention>();
    HashMap<String, Entity> gClusters = new HashMap<String, Entity>();
    HashMap<String, Entity> hwClusters = new HashMap<String, Entity>();
    HashMap<String, Entity> lClusters = new HashMap<String, Entity>();
    // First pass = exact matches 
    for (Mention m : doc.getMentions()) {
      String mentionString = m.gloss().toLowerCase();
      if (gClusters.containsKey(mentionString)) {
        Entity e = gClusters.get(mentionString);
        Set<Mention> eMentions = e.mentions;
        if (eMentions.size() == 1) {
          for (Mention singleMention : eMentions) {
            singles.remove(singleMention);
          }
        }
        mentions.put(m, m.markCoreferent(e));
      } else {
        singles.add(m);
        ClusteredMention newCluster = m.markSingleton();
        mentions.put(m, newCluster);
        Entity newEntity = newCluster.entity;
        gClusters.put(mentionString, newEntity);
        hwClusters.put(m.headWord().toLowerCase(), newEntity);
        lClusters.put(m.sentence.lemmas.get(m.headWordIndex), newEntity);
      }
    }
    // Second Pass == head word lemma matches
    Set<Mention> singlesCopy = new HashSet();
    singlesCopy.addAll(singles);
    for (Mention m : singlesCopy) { 
      if (! singles.contains(m)) continue;
      String lemma = m.sentence.lemmas.get(m.headWordIndex);
      if (lClusters.containsKey(lemma)) {
        Entity e = lClusters.get(lemma);
        Set<Mention> eMentions = e.mentions;
        if (eMentions.size() == 1) {
          for (Mention singleMention : eMentions) {
            singles.remove(singleMention);
          }
        }
        m.removeCoreference();
        mentions.put(m, m.markCoreferent(e));
        singles.remove(m);
      }
    }
    // Third Pass == exact head word matches 
    singlesCopy = new HashSet();
    singlesCopy.addAll(singles);
    for (Mention m : singlesCopy) { 
      if (! singles.contains(m)) continue;
      String headWord = m.headWord().toLowerCase();
      if (hwClusters.containsKey(headWord)) {
        Entity e = hwClusters.get(headWord);
        Set<Mention> eMentions = e.mentions;
        if (eMentions.size() == 1) {
          for (Mention singleMention : eMentions) {
            singles.remove(singleMention);
          }
        }
        m.removeCoreference();
        mentions.put(m, m.markCoreferent(e));
        singles.remove(m);
      }
    } 
    // Third Pass == head matching from headMatches 
    singlesCopy = new HashSet();
    singlesCopy.addAll(singles);
    for (Mention m : singlesCopy) { 
      if (! singles.contains(m)) continue;
      String headWord = m.headWord().toLowerCase(); 
      if (headMatches.containsKey(headWord)) { 
        for (String match : headMatches.get(headWord)) {
          if (hwClusters.containsKey(match)) {
            Entity e = hwClusters.get(match);
            Set<Mention> eMentions = e.mentions;
            if (eMentions.size() == 1) {
              for (Mention singleMention : eMentions) {
                singles.remove(singleMention);
              }
            }
            m.removeCoreference();
            mentions.put(m, m.markCoreferent(e));
            singles.remove(m);
            break;
          }
        } 
      }
    }

    System.out.println("\n\n\n-----------------------------HERE LIE THE EXPERIMENTS !!!!!!!!!!!!!");
    HashMap<Pair<String, String>, List<Mention>> ppn = new HashMap<Pair<String, String>, List<Mention>>();
    for (Mention m : doc.getMentions()) {
      Sentence s = m.sentence; 
      for (int i = m.beginIndexInclusive; i < m.endIndexExclusive; i++) {
        Sentence.Token t = s.tokens.get(i);
        String w = s.words.get(i);
        String ner = s.nerTags.get(i);
        if (t.isProperNoun()) {
          Pair<String, String> wNer = new Pair<String, String>(w, ner);
          if (ppn.containsKey(wNer)) {
            ppn.get(wNer).add(m);
          } else {
            ppn.put(wNer, new ArrayList<Mention>());
            ppn.get(wNer).add(m);
          }
        }
      }
    }
    for (Pair<String, String> wNer : ppn.keySet()) {
      if (ppn.get(wNer).size() > 1) {
        System.out.println(wNer);
        System.out.println(ppn.get(wNer));
        System.out.println("---");
        HashSet<Entity> toMerge = new HashSet<Entity>();
        for (Mention mProp : ppn.get(wNer)) {
          toMerge.add(mentions.get(mProp).entity); 
        }
        while (toMerge.size() > 1) { 
          Entity mergeInto = null;
          HashSet<Entity> toMergeCopy = new HashSet<Entity>();
          toMergeCopy.addAll(toMerge);
          for (Entity e : toMergeCopy) {
            if (mergeInto == null) {
              mergeInto = e;
              continue;
            }
            Set<Mention> eMentions = e.mentions;  
            for (Mention eMention : eMentions) {
              eMention.removeCoreference();
              mentions.put(eMention, eMention.markCoreferent(mergeInto));
            }
            toMerge.remove(e);
          }
        } 
      }
    }
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
    return mentionList;
	}

}
