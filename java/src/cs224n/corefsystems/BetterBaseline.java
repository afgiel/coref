package cs224n.corefsystems;

import java.util.Collection;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;


import cs224n.coref.ClusteredMention;
import cs224n.coref.Document;
import cs224n.coref.*;
import cs224n.util.Pair;
import cs224n.util.CounterMap;

public class BetterBaseline implements CoreferenceSystem {

  HashMap<String, List<String>> headMatches;

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
	}

	@Override
	public List<ClusteredMention> runCoreference(Document doc) {
	  HashSet<ClusteredMention> mentions = new HashSet<ClusteredMention>();
    HashMap<String, Entity> hwClusters = new HashMap<String, Entity>();
    HashMap<String, Entity> clusters = new HashMap<String, Entity>();
    for (Mention m : doc.getMentions()) {
      String headWord = m.headWord();  
      String mentionString = m.gloss();
      if (headMatches.containsKey(headWord)) { 
        boolean added = false;
        for (String match : headMatches.get(headWord)) {
          if (hwClusters.containsKey(match)) {
            mentions.add(m.markCoreferent(hwClusters.get(match)));  
            added = true;
            break;
          }
        }
        if (!added) {
          ClusteredMention newCluster = m.markSingleton();
          hwClusters.put(headWord, newCluster.entity);
          mentions.add(newCluster);
        }
      } else if (clusters.containsKey(mentionString)) {
        mentions.add(m.markCoreferent(clusters.get(mentionString)));
      } else {
        ClusteredMention newCluster = m.markSingleton();
        mentions.add(newCluster);
        clusters.put(mentionString,newCluster.entity);
      }
    }
    
    List<ClusteredMention> mentionList = new ArrayList<ClusteredMention>();
    mentionList.addAll(mentions);
    return mentionList;
	}

}
