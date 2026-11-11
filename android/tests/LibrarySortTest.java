import com.rakotoplayer.android.Library;
import com.rakotoplayer.android.MediaEntry;
import java.util.*;

public final class LibrarySortTest {
  static MediaEntry media(String id,String title,long size,long duration,long added){MediaEntry e=new MediaEntry(id,title);e.size=size;e.durationMs=duration;e.addedAt=added;return e;}
  static void check(boolean value,String label){if(!value)throw new AssertionError(label);}
  public static void main(String[] args){
    MediaEntry a=media("a","Aube",150,500,10),b=media("b","Brume",5000000000L,100,20),c=media("c","Collines",500,0,30);
    List<MediaEntry> entries=new ArrayList<>(Arrays.asList(b,c,a));Library.sort(entries,0,true);check(entries.get(0)==a,"noms A-Z");Library.sort(entries,0,false);check(entries.get(0)==c,"noms Z-A");
    Library.sort(entries,2,false);check(entries.get(0)==b,"tailles longues sans dépassement");Library.sort(entries,2,true);check(entries.get(0)==a,"tailles croissantes");
    Library.sort(entries,1,true);check(entries.get(0)==b&&entries.get(2)==c,"durée croissante et inconnue en fin");Library.sort(entries,1,false);check(entries.get(0)==a&&entries.get(2)==c,"durée décroissante et inconnue en fin");
    Library.sort(entries,3,false);check(entries.get(0)==c,"récent en premier");Library.sort(entries,3,true);check(entries.get(0)==a,"ancien en premier");System.out.println("Bibliothèque : tris nom, taille > 4 Go, durée, date et inversions vérifiés sur le code livré.");
  }
}
