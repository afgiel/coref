package cs224n.corefsystems;

import java.util.Collection;
import java.util.List;
import java.util.ArrayList;

import cs224n.coref.ClusteredMention;
import cs224n.coref.Document;
import cs224n.coref.Entity;
import cs224n.coref.Mention;
import cs224n.util.Pair;

public class AllSingleton implements CoreferenceSystem {

	@Override
	public void train(Collection<Pair<Document, List<Entity>>> trainingData) {
	}

	@Override
	public List<ClusteredMention> runCoreference(Document doc) {
    List<ClusteredMention> mentions = new ArrayList<ClusteredMention>();
    for (Mention m : doc.getMentions()) {
      mentions.add(m.markSingleton()); 
    }
    return mentions;
	}

}
