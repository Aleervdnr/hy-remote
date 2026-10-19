package local.hyremote;

import android.content.Context;
import android.util.Base64;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.math.BigInteger;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.*;
import java.util.Arrays;

/** Cliente ADB local. Solo acepta órdenes construidas por RemoteService. */
class AdbClient {
    static final int CNXN=0x4e584e43, AUTH=0x48545541, OPEN=0x4e45504f, OKAY=0x59414b4f, WRTE=0x45545257, CLSE=0x45534c43;
    static volatile boolean ready;
    final Context context;
    KeyPair keys;
    AdbClient(Context context){this.context=context;}
    synchronized void keys()throws Exception{
        if(keys!=null)return;
        File file=new File(context.getFilesDir(),"adb-key.pk8");KeyFactory factory=KeyFactory.getInstance("RSA");
        if(file.exists()){
            byte[] encoded=new byte[(int)file.length()];try(DataInputStream in=new DataInputStream(new FileInputStream(file))){in.readFully(encoded);}
            java.security.interfaces.RSAPrivateKey key=(java.security.interfaces.RSAPrivateKey)factory.generatePrivate(new PKCS8EncodedKeySpec(encoded));
            keys=new KeyPair(factory.generatePublic(new RSAPublicKeySpec(key.getModulus(),BigInteger.valueOf(65537))),key);
        }else{
            KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);keys=generator.generateKeyPair();
            try(FileOutputStream out=context.openFileOutput("adb-key.pk8",Context.MODE_PRIVATE)){out.write(keys.getPrivate().getEncoded());}
        }
    }
    byte[] publicKey(){
        RSAPublicKey key=(RSAPublicKey)keys.getPublic();BigInteger n=key.getModulus(),two32=BigInteger.ONE.shiftLeft(32);
        ByteBuffer b=ByteBuffer.allocate(524).order(ByteOrder.LITTLE_ENDIAN);b.putInt(64);b.putInt(two32.subtract(n.mod(two32).modInverse(two32)).intValue());
        putNumber(b,n);putNumber(b,BigInteger.ONE.shiftLeft(4096).mod(n));b.putInt(key.getPublicExponent().intValue());
        return (Base64.encodeToString(b.array(),Base64.NO_WRAP)+" hyremote@projector\0").getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    }
    void putNumber(ByteBuffer b,BigInteger value){for(int i=0;i<64;i++)b.putInt(value.shiftRight(i*32).intValue());}
    byte[] sign(byte[] token)throws Exception{
        if(token.length!=20)throw new IOException("Token ADB inválido");
        byte[] prefix=new byte[]{0x30,0x21,0x30,0x09,0x06,0x05,0x2b,0x0e,0x03,0x02,0x1a,0x05,0x00,0x04,0x14};
        Signature signature=Signature.getInstance("NONEwithRSA");signature.initSign(keys.getPrivate());signature.update(prefix);signature.update(token);return signature.sign();
    }
    static class Packet{int command,a,b;byte[] data;}
    Packet read(InputStream in)throws IOException{
        DataInputStream input=new DataInputStream(in);byte[] header=new byte[24];input.readFully(header);ByteBuffer h=ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        Packet p=new Packet();p.command=h.getInt();p.a=h.getInt();p.b=h.getInt();int length=h.getInt(),checksum=h.getInt(),magic=h.getInt();
        if(magic!=(p.command^0xffffffff)||length<0||length>1024*1024)throw new IOException("Paquete ADB inválido");
        p.data=new byte[length];input.readFully(p.data);int sum=0;for(byte value:p.data)sum+=value&255;if(sum!=checksum)throw new IOException("Checksum ADB inválido");return p;
    }
    void send(OutputStream out,int command,int a,int b,byte[] data)throws IOException{
        int checksum=0;for(byte value:data)checksum+=value&255;ByteBuffer h=ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
        h.putInt(command).putInt(a).putInt(b).putInt(data.length).putInt(checksum).putInt(command^0xffffffff);out.write(h.array());out.write(data);out.flush();
    }
    synchronized String shell(String command)throws Exception{
        try{keys();}catch(Exception e){android.util.Log.w("HYRemote", "ADB key: "+e.getClass().getSimpleName()+": "+e.getMessage());throw e;}try(Socket socket=new Socket()){
            socket.connect(new InetSocketAddress("127.0.0.1",5555),2000);socket.setSoTimeout(12000);InputStream in=socket.getInputStream();OutputStream out=socket.getOutputStream();
            send(out,CNXN,0x01000000,4096,"host::\0".getBytes("UTF-8"));int auth=0;
            while(true){Packet p=read(in);if(p.command==CNXN)break;if(p.command!=AUTH||p.a!=1||auth>1)throw new IOException("Falta autorizar HY Remote en el proyector");send(out,AUTH,auth++==0?2:3,0,auth==1?sign(p.data):publicKey());}
            ready=true;send(out,OPEN,1,0,("shell:"+command+"\0").getBytes("UTF-8"));ByteArrayOutputStream result=new ByteArrayOutputStream();
            while(true){Packet p=read(in);if(p.command==WRTE){if(result.size()+p.data.length>65536)throw new IOException("Respuesta demasiado larga");result.write(p.data);send(out,OKAY,1,p.a,new byte[0]);}else if(p.command==CLSE){send(out,CLSE,1,p.a,new byte[0]);break;}else if(p.command!=OKAY)throw new IOException("Respuesta ADB inesperada");}
            return result.toString("UTF-8");
        }catch(Exception e){ready=false;android.util.Log.w("HYRemote", "ADB: "+e.getClass().getSimpleName()+": "+e.getMessage());throw e;}
    }
}
