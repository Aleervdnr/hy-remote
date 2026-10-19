const fs=require('fs'),vm=require('vm'),assert=require('assert');
const html=fs.readFileSync('assets/index.html','utf8');
const nodes=new Map(),calls=[],timers=[];let clock=0;
function node(id=''){return {value:id==='sensitivity'?'1':'',hidden:false,disabled:false,childElementCount:0,dataset:{},style:{setProperty(){}},classList:{toggle(){},add(){},remove(){}},setAttribute(){},append(){this.childElementCount++},replaceChildren(){this.childElementCount=0},focus(){},addEventListener(){},setPointerCapture(){},getBoundingClientRect(){return {height:170}}}}
function get(id){if(!nodes.has(id))nodes.set(id,node(id));return nodes.get(id)}
const document={body:node(),hidden:false,getElementById:get,querySelector:()=>node(),querySelectorAll:()=>[],createElement:()=>node(),addEventListener(){},documentElement:node()};
const sandbox={document,history:{replaceState(){}},location:{pathname:'/'},navigator:{},localStorage:{getItem:()=>null,setItem(){}},window:{innerHeight:844,addEventListener(){}},clearTimeout(){},performance:{now:()=>clock},setInterval(){},setTimeout:fn=>timers.push(fn),fetch:async(path,options={})=>{const data=options.body?JSON.parse(options.body):null;if(path==='/api/pointer')calls.push(data);return {ok:true,json:async()=>path==='/api/apps'?{apps:[],favorites:[]}:path==='/api/status'?{connected:true,target:'test',adbReady:true}:{ok:true,x:640,y:360,width:1280,height:720}}}};
vm.createContext(sandbox);vm.runInContext(html.match(/<script>([\s\S]*?)<\/script>/)[1],sandbox);
async function flush(){for(let i=0;i<30;i++){await Promise.resolve();const pending=timers.splice(0);for(const fn of pending)fn()}}
function event(id,x,y){return {pointerId:id,clientX:x,clientY:y,button:0,preventDefault(){}}}
(async()=>{
 await flush();vm.runInContext("tab('touchpad')",sandbox);await flush();calls.length=0;
 const area=get('touchArea');
 area.onpointerdown(event(1,10,10));area.onpointermove(event(1,40,30));clock=100;area.onpointerup(event(1,40,30));await flush();assert(calls.some(c=>c.action==='move'&&c.dx===30&&c.dy===20));assert(!calls.some(c=>c.action==='click'));
 calls.length=0;area.onpointerdown(event(1,10,10));clock+=100;area.onpointerup(event(1,10,10));await flush();assert.equal(calls.filter(c=>c.action==='click').length,1);
 calls.length=0;area.onpointerdown(event(1,10,10));area.onpointerdown(event(2,50,10));area.onpointermove(event(1,10,70));area.onpointerup(event(1,10,70));area.onpointerup(event(2,50,10));await flush();assert(calls.some(c=>c.action==='scroll'&&c.dy===1));assert(!calls.some(c=>c.action==='click'));
 calls.length=0;area.onpointerdown(event(1,10,10));area.onpointercancel(event(1,10,10));await flush();assert(!calls.some(c=>c.action==='click'));
 calls.length=0;vm.runInContext("tab('control');pointerAction('scroll',{dy:-1})",sandbox);await flush();assert(calls.some(c=>c.action==='scroll'&&c.dy===-1));
 calls.length=0;vm.runInContext("tab('keyboard');pointerAction('scroll',{dy:1})",sandbox);await flush();assert(calls.some(c=>c.action==='scroll'&&c.dy===1));
 console.log('Scroll global en Control y Teclado verificado.');
 console.log('Gestos verificados: arrastre sin clic, toque con clic, dos dedos con scroll sin clic y cancelación sin clic.');
})().catch(e=>{console.error(e);process.exitCode=1});
