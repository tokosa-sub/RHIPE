package org.godhuli.rhipe;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import org.godhuli.rhipe.REXPProtos.REXP;
import java.io.*;
import java.util.*;
import java.net.*;
import org.apache.hadoop.fs.FileUtil;


import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.io.*;
import org.apache.hadoop.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class PersonalServer {
    protected static final Log LOG = LogFactory.getLog(PersonalServer.class.getName());
    byte[] bbuf;
    DataOutputStream _err,_toR;
    DataInputStream _fromR;
    REXP yesalive;
    FileUtils fu;
    int buglevel;
    Hashtable<String, SequenceFile.Reader> seqhash;
    public static String getPID() throws IOException,InterruptedException {
	//Taken from http://www.coderanch.com/t/109334/Linux-UNIX/UNIX-process-ID-java-program
	Vector<String> commands=new Vector<String>();
	commands.add("/bin/bash");
	commands.add("-c");
	commands.add("echo $PPID");
	ProcessBuilder pb=new ProcessBuilder(commands);
	Process pr=pb.start();
	pr.waitFor();
	if (pr.exitValue()==0) {
	    BufferedReader outReader=new BufferedReader(new InputStreamReader(pr.getInputStream()));
	    return outReader.readLine().trim();
	} else {
	    throw new IOException("Problem getting PPID");
	}
    }
    public void docrudehack(String temp) throws IOException{
	FileWriter outFile = new FileWriter(temp);
	String x = "DONE";
	outFile.write(x,0,x.length());
	outFile.flush();outFile.close();
    }
	
    public PersonalServer(String ipaddress,String tempfile,String tempfile2,int bugl) throws InterruptedException,
								   FileNotFoundException,UnknownHostException, SecurityException,IOException{
	bbuf = new byte[100];
	this.buglevel = bugl;
	seqhash = new Hashtable<String, SequenceFile.Reader>();
	REXP.Builder thevals   = REXP.newBuilder();
	thevals.setRclass(REXP.RClass.LOGICAL);
	thevals.addBooleanValue( REXP.RBOOLEAN.T);
	yesalive = thevals.build();
	if(buglevel > 10)
	    LOG.info("Calling FileUtils");
	fu = new FileUtils(new Configuration());
	if(buglevel > 10)
	    LOG.info("Got FileUtils object:"+fu);

	ServerSocket fromRsock,errsock,toRsock;
	if(buglevel > 10)
	    LOG.info("Creating listening and writing sockets");
	fromRsock = new ServerSocket(0,0,InetAddress.getByName(ipaddress));
	toRsock  = new ServerSocket(0);
	errsock = new ServerSocket(0);
	if(buglevel > 10)
	    LOG.info("Got fromRsock="+fromRsock+" toRsock="+toRsock+" errsock="+errsock);
	FileWriter outFile = new FileWriter(tempfile);
	if(buglevel > 10)
	    LOG.info("Writing information to file:"+outFile);
	String x = "fromR toR err PID\n";
	outFile.write(x,0,x.length());
	x = fromRsock.getLocalPort()+" "+toRsock.getLocalPort()+" "+errsock.getLocalPort()+" "+ getPID()+"\n";
	outFile.write(x,0,x.length());
	outFile.flush();outFile.close();
	docrudehack(tempfile2);
	if(buglevel > 10)
	    LOG.info("Finished with crudehack by creating a file called "+tempfile2);
	Socket a = fromRsock.accept();
	_fromR = new DataInputStream(new BufferedInputStream(a.getInputStream(),1024));
	 a = toRsock.accept();
	_toR = new DataOutputStream(new BufferedOutputStream(a.getOutputStream(),1024));
	 a = errsock.accept();
	 _err = new DataOutputStream(new BufferedOutputStream(a.getOutputStream(),1024));
	if(buglevel > 10)
	    LOG.info("Now waiting on all sockets");
    }
    public void rhmropts(REXP r) throws Exception{
	REXP b = fu.mapredopts();
	send_result(b);
    }
    public void rhcat(REXP r) throws Exception {
	final int buff = r.getRexpValue(2).getIntValue(0);
	final int mx = r.getRexpValue(3).getIntValue(0);

	    for(int i=0; i<r.getRexpValue(1).getStringValueCount();i++){
		Path srcPattern = new Path(r.getRexpValue(1).getStringValue(i).getStrval());
		new DelayedExceptionThrowing() {
		    void process(Path p, FileSystem srcFs) throws IOException {
			if (srcFs.getFileStatus(p).isDir()) {
			    throw new IOException("Source must be a file.");
			}
			// System.err.println("INPUT="+p);
			printToStdout(srcFs.open(p),buff,mx);
		    }
		}.globAndProcess(srcPattern, srcPattern.getFileSystem(fu.getConf()));
	    }
	    _toR.writeInt(-1);
	    _toR.flush();
    }
    private void printToStdout(InputStream in,int buffsize,int mx) throws IOException {
	try {
	    byte buf[] = new byte[buffsize];
	    int bytesRead = in.read(buf);
	    while (bytesRead >= 0) {
		// System.err.println("Wrote "+bytesRead);
		_toR.writeInt(bytesRead);
		_toR.write(buf, 0, bytesRead);
		_toR.flush();
		if(mx > -1 && bytesRead >=mx) break;
		bytesRead = in.read(buf);
	    }
	} finally {
	    in.close();
	}
    }
    // private void printToStdout(InputStream in) throws IOException {
    //     BufferedReader br;
    //     PrintStream ps=null;
    //     try {
    //         br = new BufferedReader(new InputStreamReader(in),1024*1024);
    //         ps = new PrintStream(_toR,true);
    //         // byte buf[] = new byte[1024*1024];                                                                                                            
    //         while(true){
    //             String line = br.readLine();
    //             if(line == null) break;
    //             ps.print(line);
    //             System.out.println(line);
    //         // int bytesRead = in.read(buf);                                                                                                                
    //         // System.err.println("Read "+bytesRead);                                                                                                       
    //         // while (bytesRead >= 0) {                                                                                                                     
    //         //  ps.write(buf, 0, bytesRead);                                                                                                                
    //         //  for(int i=0;i < bytesRead;i++) System.err.print((char)buf[i]);                                                                              
    //         //  if (ps.checkError()) {                                                                                                                      
    //         //      throw new IOException("Unable to write to output stream.");                                                                             
    //         //  }                                                                                                                                           
    //         //  bytesRead = in.read(buf);                                                                                                                   
    //         }
    //     } finally {
    //         if(ps !=null) ps.flush();
    //         in.close();
    //     }
    // }

    // void copy(String srcf, String dstf, Configuration conf) throws IOException {
    // 	Path srcPath = new Path(srcf);
    // 	FileSystem srcFs = srcPath.getFileSystem(getConf());
    // 	Path dstPath = new Path(dstf);
    // 	FileSystem dstFs = dstPath.getFileSystem(getConf());
    // 	Path [] srcs = FileUtil.stat2Paths(srcFs.globStatus(srcPath), srcPath);
    // 	if (srcs.length > 1 && !dstFs.isDirectory(dstPath)) {
    // 	    throw new IOException("When copying multiple files, " 
    // 				  + "destination should be a directory.");
    // 	}
    // 	for(int i=0; i<srcs.length; i++) {
    // 	    FileUtil.copy(srcFs, srcs[i], dstFs, dstPath, false, conf);
    // 	}
    // }
    // public void rhcp(REXP r) throws Exception{
    // 	String[] argv = new String[r.getRexpValue(1).getStringValueCount()+1];
    // 	for(int i=0;i<argv.length;i++) 
    // 	    argv[i] = r.getRexpValue(1).getStringValue(i).getStrval();
    // 	argv[argv.length] = r.getRexpValue(2).getStringValue(0).getStrval();
    // 	int i = 0;
    // 	String dest = argv[argv.length-1];
    // 	if (argv.length > 3) {
    // 	    Path dst = new Path(dest);
    // 	    if (!fu.getConf().isDirectory(dst)) {
    // 		throw new IOException("When copying multiple files, " 
    //                           + "destination " + dest + " should be a directory.");
    // 	    }
    // 	}
    // 	for (; i < argv.length - 1; i++) {
    // 	    try {
    // 		copy(argv[i], dest, conf);
    // 	    } catch (RemoteException e) {
    //     exitCode = -1;
    //     try {
    //       String[] content;
    //       content = e.getLocalizedMessage().split("\n");
    //       System.err.println(cmd.substring(1) + ": " +
    //                          content[0]);
    //     } catch (Exception ex) {
    //       System.err.println(cmd.substring(1) + ": " +
    //                          ex.getLocalizedMessage());
    //     }
    //   } catch (IOException e) {
    //     //
    //     // IO exception encountered locally.
    //     //
    //     exitCode = -1;
    //     System.err.println(cmd.substring(1) + ": " +
    //                        e.getLocalizedMessage());
    //   }
    // }
    // return exitCode;
    // }

    // private void printToStdout(InputStream in) throws IOException {
    // 	try {
    // 	    IOUtils.copyBytes(in, System.out, fu.getConf(), false);
    // 	} finally {
    // 	    in.close();
    // 	}
    // }

    // public void rhmerge(REXP r) throws Exception{
    // 	Path srcPattern = new Path(src);
    // 	new DelayedExceptionThrowing() {
    // 	    void process(Path p, FileSystem srcFs) throws IOException {
    // 		if (srcFs.getFileStatus(p).isDir()) {
    // 		    throw new IOException("Source must be a file.");
    // 		}
    // 		printToStdout(srcFs.open(p));
    // 	    }
    // 	}.globAndProcess(srcPattern, getSrcFileSystem(srcPattern, true));
    // }

    // public void rhmv(REXP r) throws Exception{
    // 	String[] fromfiles = new String[r.getRexpValue(1).getStringValueCount()+1];
    // 	for(int i=0;i<fromfiles.length;i++) 
    // 	    fromfiles[i] = r.getRexpValue(1).getStringValue(i).getStrval();
    // 	fromfiles[fromfiles.length] = r.getRexpValue(2).getStringValue(i).getStrval();
    // 	fu.getFsShell().rename(fromfiles, fu.getConf());

    // }

    public void rhls(REXP r) throws Exception{
	String[] result0 = fu.ls(r.getRexpValue(1) // This is a string vector
				 ,r.getRexpValue(2).getIntValue(0));
	REXP b = RObjects.makeStringVector(result0);
	send_result(b);

    }
    public void rhdel(REXP r) throws Exception{
	for(int i = 0;i <r.getRexpValue(1).getStringValueCount();i++){
	    String s = r.getRexpValue(1).getStringValue(i).getStrval();
	    fu.delete(s,true);
	}
	send_result("OK");
    }

    public void rhget(REXP r) throws Exception{
	String src = r.getRexpValue(1).getStringValue(0).getStrval();
	String dest = r.getRexpValue(2).getStringValue(0).getStrval();
	System.err.println("Copying "+src+" to "+dest);
	fu.copyMain(src,dest);
	send_result("OK");
    }
    public void rhput(REXP r) throws Exception{
	String[] locals = new String[r.getRexpValue(1).getStringValueCount()];
	for(int i=0;i<locals.length;i++) 
	    locals[i] = r.getRexpValue(1).getStringValue(i).getStrval();
	String dest2 = r.getRexpValue(2).getStringValue(0).getStrval();
	REXP.RBOOLEAN overwrite_ = r.getRexpValue(3).getBooleanValue(0);
	boolean overwrite;
	if(overwrite_==REXP.RBOOLEAN.F)
	    overwrite=false;
	else if(overwrite_==REXP.RBOOLEAN.T)
	    overwrite=true;
	else
	    overwrite=false;
	fu.copyFromLocalFile(locals,dest2,overwrite);
	send_result("OK");
    }
    public void sequenceAsBinary(REXP r) throws Exception{ //works
	Configuration cfg = new Configuration();
	int n = r.getRexpValue(1).getStringValueCount();
	String[] infile = new String[n];
	for(int i=0;i< n;i++) {
	    infile[i] = r.getRexpValue(1).getStringValue(i).getStrval();
	}
	int maxnum = r.getRexpValue(2).getIntValue(0);
	int bufsz = r.getRexpValue(3).getIntValue(0);
	// as this rexp is written into
	DataOutputStream cdo = new DataOutputStream(new java.io.BufferedOutputStream(_toR,1024*1024));
	int counter=0;
	boolean endd=false;
	RHBytesWritable k=new RHBytesWritable();
	RHBytesWritable v=new RHBytesWritable();
	for(int i=0; i <infile.length;i++){
	    SequenceFile.Reader sqr = new SequenceFile.Reader(FileSystem.get(cfg) ,new Path(infile[i]), cfg);
	    while(true){
		boolean gotone = sqr.next((Writable)k,(Writable)v);
		if(gotone){
		    counter++;
		    k.writeAsInt(cdo);
		    v.writeAsInt(cdo);
		    cdo.flush();
		}else break;
		if(maxnum >0 && counter >= maxnum) {
		    endd=true;
		    break;
		}
	    }
	    sqr.close();
	    if(endd) break;
	}
	cdo.writeInt(0);
	cdo.flush();
    }

    public void rhopensequencefile(REXP r) throws Exception{
	// System.out.println("----Called-----");
	String  name = r.getRexpValue(1).getStringValue(0).getStrval();
	Configuration cfg = new Configuration();
	SequenceFile.Reader sqr = new SequenceFile.Reader(FileSystem.get(cfg) ,new Path(name), cfg);
	seqhash.put(name, sqr);
	send_result("OK");
    }
    public void rhgetnextkv(REXP r) throws Exception{
	String name = r.getRexpValue(1).getStringValue(0).getStrval();
	int quant = r.getRexpValue(2).getIntValue(0);
	SequenceFile.Reader sqr = seqhash.get(name);
	RHBytesWritable k=new RHBytesWritable();
	RHBytesWritable v=new RHBytesWritable();
	DataOutputStream cdo = new DataOutputStream(new java.io.BufferedOutputStream(_toR,1024*1024));
	if(sqr!=null){
	    for(int i=0; i < quant;i++){
		boolean gotone = sqr.next((Writable)k,(Writable)v);
		if(gotone){
		    k.writeAsInt(cdo);
		    v.writeAsInt(cdo);
		    cdo.flush();
		}else{
		    sqr.close();
		    seqhash.remove(name);
		    break;
		}
	    }
	}
	cdo.writeInt(0);
	cdo.flush();
    }

    public void rhclosesequencefile(REXP r) throws Exception{
	String name = r.getRexpValue(1).getStringValue(0).getStrval();
	SequenceFile.Reader sqr = seqhash.get(name);
	if(sqr!=null){
	    try{
		sqr.close();
		seqhash.remove(name);
	    }catch(Exception e){}
	}
	    send_result("OK");
    }
    public void rhstatus(REXP r) throws Exception{
	REXP jid = r.getRexpValue(1);
	REXP result = fu.getstatus(jid);
	send_result(result);
    }

    public void rhjoin(REXP r) throws Exception{
	REXP result = fu.joinjob(r.getRexpValue(1));
	send_result(result);
    }

    public void rhkill(REXP r) throws Exception{
	REXP jid = r.getRexpValue(1);
	fu.killjob(jid);
	send_result("OK");
    }

    
    public void send_alive() throws Exception{
	try{
	    
	    _toR.writeByte(1);
	    _toR.flush();
	}catch(IOException e){
	    System.err.println("RHIPE: Could not tell R it is alive");
	    System.exit(1);
	}
    }
    
    public void send_error_message(Exception e){
	ByteArrayOutputStream bs = new ByteArrayOutputStream();
	e.printStackTrace(new PrintStream(bs));
	String s = bs.toString();
	send_error_message(s);
    }
    public void send_error_message(String s){
	REXP clattr = RObjects.makeStringVector("worker_error");
	REXP r = RObjects.addAttr(RObjects.buildStringVector(new String[]{s}), "class",clattr).build();
	System.err.println(s);
	sendMessage(r,true);
    }

    public void sendMessage(REXP r){
	sendMessage(r, false);
    }
    public void sendMessage(REXP r,boolean bb) {
	try{
	    byte[] b = r.toByteArray();
	    DataOutputStream dos = _toR;
	    // if(bb) dos = _err;
	    if(bb) dos.writeInt(-b.length); else dos.writeInt(b.length);
	    dos.write(b,0,b.length);
	    dos.flush();
	}catch(IOException e){
	    System.err.println("RHIPE: Could not send data back to R master, sending to standard error");
	    System.err.println(r);
	    System.exit(1);
	}
    }

    public void send_result(String s){
	REXP r = RObjects.makeStringVector(s);
	send_result(r);
    }
    public void send_result(REXP r){
	// we create a list of class "worker_result"
	// it is a list of element given by s
	// all results are class worker_result and are a list
	REXP.Builder thevals   = REXP.newBuilder();
	thevals.setRclass(REXP.RClass.LIST);
	thevals.addRexpValue(r);
	RObjects.addAttr(thevals,"class",RObjects.makeStringVector("worker_result"));
	sendMessage(thevals.build());
    }
    public void rhgetkeys(REXP rexp00) throws Exception{
	REXP rexp0 = rexp00.getRexpValue(1);
	REXP keys = rexp0.getRexpValue(0); //keys
	REXP paths = rexp0.getRexpValue(1); //paths to read from
	String tempdest = rexp0.getRexpValue(2).getStringValue(0).getStrval(); //tempdest
	REXP.RBOOLEAN b = rexp0.getRexpValue(3).getBooleanValue(0); //as sequence or binary
	Configuration c=fu.getConf();
	DataOutputStream out = _toR;
	c.setInt("io.map.index.skip",rexp0.getRexpValue(4).getIntValue(0)); //skipindex
	String[] pnames = new String[paths.getStringValueCount()];
	for(int i=0;i< pnames.length;i++){
	    pnames[i] = paths.getStringValue(i).getStrval();
	}
	MapFile.Reader[] mr = RHMapFileOutputFormat.getReaders(pnames,c);

	int numkeys = keys.getRexpValueCount();
	RHBytesWritable k = new RHBytesWritable();
	RHBytesWritable v = new RHBytesWritable();
	boolean closeOut = false;
	if(b==REXP.RBOOLEAN.F){ //binary style
	    if(out==null){
		closeOut=true;
		out = new DataOutputStream(new FileOutputStream(tempdest));
	    }
	    for(int i=0; i < numkeys; i++){
		k.set(keys.getRexpValue(i).getRawValue().toByteArray());
		RHMapFileOutputFormat.getEntry(mr,k,v);
		k.writeAsInt(out);
		v.writeAsInt(out);
	    }
	    if (closeOut) 
		out.close();
	    else {
		out.writeInt(0);
		out.flush();
	    }
	}else{// these will be written out as a sequence file
	    RHWriter rw = new RHWriter(tempdest,fu.getConf());
	    SequenceFile.Writer w = rw.getSQW();
	    for(int i=0; i < numkeys; i++){
		k.set(keys.getRexpValue(i).getRawValue().toByteArray());
		RHMapFileOutputFormat.getEntry(mr,k,v);
		w.append(k,v);
	    }
	    rw.close();
	}
    }

    public void rhex(REXP rexp0) throws Exception{
	String[] zonf = new String[]{rexp0.getRexpValue(1).getStringValue(0).getStrval()}; 
	int result = RHMR.fmain(zonf);
	send_result(""+result);
    }

    public void binaryAsSequence(REXP r) throws Exception{ //works
	Configuration cfg = new Configuration();
	String ofolder= r.getRexpValue(1).getStringValue(0).getStrval();
	int groupsize = r.getRexpValue(2).getIntValue(0);
	int howmany = r.getRexpValue(3).getIntValue(0);
	int N = r.getRexpValue(4).getIntValue(0);
	DataInputStream  in = _fromR;
	int count=0;
	// System.out.println("Got"+r);
	// System.out.println("Waiting for input");
	for(int i=0;i < howmany-1;i++){
	    String f = ofolder+"/"+i;
	    RHWriter w = new RHWriter(f,cfg);
	    w.doWriteFile(in,groupsize);
	    count=count+groupsize;
	    w.close();
	}

	if(count < N){
	    count=N-count;
	    String f = ofolder+"/"+(howmany-1);
	    RHWriter w = new RHWriter(f,cfg);
	    w.doWriteFile(in,count);
	    w.close();
	}
	send_result("OK");
    }

    public void startme(){
	while(true){
	    try{
	 	int size= _fromR.readInt();
		if(size> bbuf.length){
		    bbuf = new byte[size];
		}
		if(size<0) break;
		else if(size == 0)
		    send_alive();
		else {
		_fromR.readFully(bbuf,0,size);
		REXP r = REXP.newBuilder().mergeFrom(bbuf,0,size).build();
		if(r.getRclass() == 	REXP.RClass.NULLTYPE)
		    send_alive();
		// the first element of list is function, the rest are arguments
		String tag = r.getRexpValue(0).getStringValue(0).getStrval();
		if(tag.equals("rhmropts")) rhmropts(r);
		else if(tag.equals("rhls")) rhls(r);
		else if(tag.equals("rhget")) rhget(r);
		else if(tag.equals("rhput")) rhput(r);
		else if(tag.equals("rhdel")) rhdel(r);
		else if(tag.equals("rhgetkeys")) rhgetkeys(r);
		else if(tag.equals("binaryAsSequence")) binaryAsSequence(r);
		else if(tag.equals("sequenceAsBinary")) sequenceAsBinary(r);
		else if(tag.equals("rhstatus")) rhstatus(r);
		else if(tag.equals("rhjoin")) rhjoin(r);
		else if(tag.equals("rhkill")) rhkill(r);
		else if(tag.equals("rhex")) rhex(r);
		else if(tag.equals("rhcat")) rhcat(r);
		else if(tag.equals("rhopensequencefile")) rhopensequencefile(r);
		else if(tag.equals("rhgetnextkv")) rhgetnextkv(r);
		else if(tag.equals("rhclosesequencefile")) rhclosesequencefile(r);


		// else if(tag.equals("rhcp")) rhcp(r);
		// else if(tag.equals("rhmv")) rhmv(r);
		// else if(tag.equals("rhmerge")) rhmerge(r);

		else send_error_message("Could not find method with name: "+tag+"\n");
		}
	}catch(EOFException e){
		System.exit(0);
	}catch (SecurityException e) {
	    send_error_message(e);
	}catch (IllegalArgumentException e) {
	    send_error_message(e);
	}catch (IllegalAccessException e) {
	    send_error_message(e);
	}catch(RuntimeException e){
	    send_error_message(e);
	}catch(IOException e){
	    send_error_message(e);
	}catch(Exception e){
	    send_error_message(e);
	}
	}
    }


    public static void main(String[] args) throws Exception{
	int buglevel = Integer.parseInt(args[3]);
	PersonalServer r = new PersonalServer(args[0],args[1],args[2],buglevel);
	while(true){
	    try{
		// LOG.info("Starting personalserver");
		r.startme();

	    }catch(Exception e){
		System.err.println(Thread.currentThread().getStackTrace());
	    }
	}
    }
    public abstract class DelayedExceptionThrowing {
	abstract void process(Path p, FileSystem srcFs) throws IOException;
	final void globAndProcess(Path srcPattern, FileSystem srcFs
				  ) throws IOException {
	    ArrayList<IOException> exceptions = new ArrayList<IOException>();
	    for(Path p : FileUtil.stat2Paths(srcFs.globStatus(srcPattern), 
					     srcPattern))
		try { process(p, srcFs); } 
		catch(IOException ioe) { exceptions.add(ioe); }
    
	    if (!exceptions.isEmpty())
		if (exceptions.size() == 1)
		    throw exceptions.get(0);
		else 
		    throw new IOException("Multiple IOExceptions: " + exceptions);
	}
    }


}