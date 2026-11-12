import com.rakotoplayer.android.MediaNames;
import com.rakotoplayer.android.ShuffleCycle;
import com.rakotoplayer.android.VerifiedRemoval;
import java.io.IOException;
import java.util.*;

public final class PlaybackPolicyTest {
  private static void check(boolean value,String label){if(!value)throw new AssertionError(label);}
  public static void main(String[] args)throws Exception{
    String name="Basa Lion - colombian.mp3",selected="Basa Lion - colombian";
    check(MediaNames.title(selected,name,"fd://89").equals(selected),"nom sélectionné stable malgré LibVLC");
    for(String technical:new String[]{"fd://89"," fd://192 ","FD://7","fd:89","fd%3A%2F%2F89","content://media/audio/42","/proc/self/fd/89"})check(MediaNames.title(technical,name,null).equals(name),"réparation : "+technical);
    check(MediaNames.readable("File: une chanson"),"vrai titre conservé");check(MediaNames.readable("89"),"titre numérique conservé");
    check(MediaNames.title("",null,"Titre du fichier").equals("Titre du fichier"),"métadonnée de secours");
    ShuffleCycle cycle=new ShuffleCycle();cycle.reset(100,0);Set<Integer> seen=new HashSet<>();seen.add(0);int current=0;for(int i=1;i<100;i++){current=cycle.next(current,1,100,false);check(current>=0&&current<100&&seen.add(current),"chaque morceau une seule fois");}check(cycle.next(current,1,100,false)==-1,"fin réelle de la file");int previous=cycle.next(current,-1,100,false);check(previous>=0&&previous!=current,"précédent depuis historique");check(cycle.next(previous,1,100,false)==current,"suivant après précédent");check(cycle.next(current,1,100,true)>=0,"nouveau cycle avec répétition");
    cycle.reset(2,0);int next=cycle.next(0,1,3,false);check(next==1||next==2,"ajout à la file");check(cycle.next(next,1,3,false)!=(next),"dernier morceau ajouté visité");
    cycle.reset(1,0);check(cycle.next(0,1,1,false)==-1,"morceau seul sans répétition");check(cycle.next(0,1,1,true)==0,"morceau seul en boucle");check(cycle.next(-1,1,0,false)==-1,"file vide");
    boolean[] removed={false},committed={false};
    try{VerifiedRemoval.verify(()->{throw new IOException("coffre corrompu");}).remove(()->{removed[0]=true;return true;},()->committed[0]=true);throw new AssertionError("corruption acceptée");}catch(IOException expected){}
    check(!removed[0]&&!committed[0],"aucune suppression si vérification échoue");
    try{VerifiedRemoval.verify(()->{}).remove(()->false,()->committed[0]=true);throw new AssertionError("suppression non confirmée acceptée");}catch(IOException expected){}check(!committed[0],"aucune finalisation sans suppression confirmée");
    List<String> order=new ArrayList<>();VerifiedRemoval.Ticket ticket=VerifiedRemoval.verify(()->order.add("verified"));ticket.remove(()->{order.add("removed");return true;},()->order.add("committed"));check(order.equals(Arrays.asList("verified","removed","committed")),"ordre du transfert");
    try{ticket.remove(()->{throw new AssertionError("double suppression");},()->{});throw new AssertionError("ticket réutilisé");}catch(IllegalStateException expected){}
    System.out.println("Lecture : noms réels, réparation fd://, aléatoire sans répétition et barrière de suppression vérifiés.");
  }
}
