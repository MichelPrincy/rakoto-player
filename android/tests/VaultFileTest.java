import com.rakotoplayer.android.VaultFile;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Real JCE tests of the same encrypted-file reader shipped on Android. */
public final class VaultFileTest {
  interface Action {void run()throws Exception;}
  static void check(boolean value,String label){if(!value)throw new AssertionError(label);}
  static void rejected(Action action,String label)throws Exception{try{action.run();throw new AssertionError("Accepté à tort : "+label);}catch(IOException|GeneralSecurityException expected){}}
  public static void main(String[] args)throws Exception{
    Path root=Path.of(args[0]);Files.createDirectories(root);byte[] key=new byte[32],plain=new byte[8*1024*1024+173];new Random(248635).nextBytes(key);new Random(792134).nextBytes(plain);
    File file=root.resolve("roundtrip.vlt").toFile();long size=VaultFile.encrypt(new ByteArrayInputStream(plain),file,key,done->{});check(size==plain.length,"taille réelle");
    try(VaultFile.Reader reader=new VaultFile.Reader(file,key,plain.length)){
      reader.verify(done->{});byte[] buffer=new byte[700003];long offset=0;while(offset<plain.length){int count=reader.read(offset,buffer,buffer.length);check(Arrays.equals(Arrays.copyOf(buffer,count),Arrays.copyOfRange(plain,(int)offset,(int)offset+count)),"lecture complète");offset+=count;}
      ByteArrayOutputStream restored=new ByteArrayOutputStream();reader.copyTo(restored,done->{});check(Arrays.equals(plain,restored.toByteArray()),"export authentifié exact");
      for(long at:new long[]{0,17,262140,262145,2*262144-7,plain.length-19}){int count=reader.read(at,buffer,buffer.length);check(Arrays.equals(Arrays.copyOf(buffer,count),Arrays.copyOfRange(plain,(int)at,(int)at+count)),"seek "+at);}
      check(reader.read(plain.length,buffer,1)==0,"fin de fichier");
    }
    byte[] encrypted=Files.readAllBytes(file.toPath());check(!Arrays.equals(Arrays.copyOf(encrypted,128),Arrays.copyOf(plain,128)),"aucun en-tête clair");
    File second=root.resolve("second.vlt").toFile();VaultFile.encrypt(new ByteArrayInputStream(plain),second,key,done->{});check(!Arrays.equals(encrypted,Files.readAllBytes(second.toPath())),"nonces distincts");
    byte[] wrong=key.clone();wrong[0]^=1;rejected(()->{try(VaultFile.Reader r=new VaultFile.Reader(file,wrong,plain.length)){r.verify(done->{});}},"mauvaise clé");
    byte[] damaged=encrypted.clone();damaged[36+400]^=1;Path bad=root.resolve("damaged.vlt");Files.write(bad,damaged);rejected(()->{try(VaultFile.Reader r=new VaultFile.Reader(bad.toFile(),key,plain.length)){r.verify(done->{});}},"bloc modifié");
    Path truncated=root.resolve("truncated.vlt");Files.write(truncated,Arrays.copyOf(encrypted,encrypted.length-8));rejected(()->{try(VaultFile.Reader ignored=new VaultFile.Reader(truncated.toFile(),key,plain.length)){}},"troncature");
    byte[] swapped=encrypted.clone();int block=262144+16;System.arraycopy(encrypted,36,swapped,36+block,block);System.arraycopy(encrypted,36+block,swapped,36,block);Path reorder=root.resolve("reordered.vlt");Files.write(reorder,swapped);rejected(()->{try(VaultFile.Reader r=new VaultFile.Reader(reorder.toFile(),key,plain.length)){r.verify(done->{});}},"blocs permutés");
    File empty=root.resolve("empty.vlt").toFile();rejected(()->VaultFile.encrypt(new ByteArrayInputStream(new byte[0]),empty,key,done->{}),"fichier vide");
    for(String suffix:new String[]{".rpi",".rpv"}){File typed=root.resolve("media"+suffix).toFile();VaultFile.encrypt(new ByteArrayInputStream(plain),typed,key,done->{});try(VaultFile.Reader reader=new VaultFile.Reader(typed,key,plain.length)){ByteArrayOutputStream restored=new ByteArrayOutputStream();reader.copyTo(restored,done->{});check(Arrays.equals(plain,restored.toByteArray()),"aller-retour "+suffix);}}
    rejected(()->{try(VaultFile.Reader reader=new VaultFile.Reader(bad.toFile(),key,plain.length)){reader.copyTo(new ByteArrayOutputStream(),done->{});}},"export d’un fichier corrompu");
    rejected(()->{try(VaultFile.Reader reader=new VaultFile.Reader(file,key,plain.length)){reader.copyTo(new ByteArrayOutputStream(),done->{throw new InterruptedIOException("coffre verrouillé");});}},"export interrompu");
    System.out.println("Coffre : chiffrement réel, lecture complète, seeks inter-blocs, nonces, mauvaises clés, corruption, troncature et permutation vérifiés.");
  }
}
