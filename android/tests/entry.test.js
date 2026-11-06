const {test}=require('node:test');const assert=require('node:assert/strict');const vm=require('node:vm');const fs=require('node:fs');const path=require('node:path');
const source=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/entry.js'),'utf8');
function run(platform,window,document,pathname='/exchangesystem/'){let redirected='';const box={window:{__njuSource:platform,...window},document,Event:class{constructor(type,options){this.type=type;}},location:{hostname:platform==='elite'?'elite.nju.edu.cn':platform==='ehall'?'ehall.nju.edu.cn':'selearning.nju.edu.cn',pathname,replace:url=>{redirected=url;}}};const result=vm.runInNewContext(source,box);return {result,redirected,box};}
test('anonymous elite application is routed to the real login homepage',()=>{const r=run('elite',{__env:{anonymous:true}},{},'/exchangesystem/index/create');assert.equal(r.redirected,'/exchangesystem/');assert.equal(r.result.state,'navigating');});
test('elite uses the actual student login control without filling an application',()=>{let clicks=0;const r=run('elite',{__env:{anonymous:true}},{querySelector:()=>({click:()=>{clicks++;}})});assert.equal(clicks,1);assert.equal(r.result.state,'navigating');});
test('authenticated elite homepage immediately permits background data reading',()=>{const r=run('elite',{__env:{anonymous:false}},{querySelector:()=>null});assert.equal(r.result.state,'read');});
test('ehall automatically opens the observed formal grades entry',()=>{let clicks=0;const entry={textContent:'我的成绩',getClientRects:()=>[{}],click:()=>{clicks++;}};const r=run('ehall',{}, {querySelectorAll:()=>[entry],querySelector:()=>null},'/ywtb-portal/official/index.html');assert.equal(clicks,1);assert.equal(r.result.state,'wait');});
test('Moodle chooses the actual SSO control rather than inventing a login URL',()=>{let clicks=0;const r=run('moodle',{}, {querySelectorAll:()=>[{textContent:'统一身份认证',click:()=>{clicks++;}}]},'/login/index.php');assert.equal(clicks,1);assert.equal(r.result.state,'navigating');});

test('school auth page waits for user even without a password field',()=>{const box={window:{__njuSource:'lms'},document:{querySelector:()=>null},location:{hostname:'authserver.nju.edu.cn',pathname:'/authserver/login'}};assert.equal(vm.runInNewContext(source,box).state,'auth');});
test('captcha form requires user verification instead of claiming ready',()=>{const r=run('moodle',{}, {querySelector:selector=>selector.includes('captcha')?{}:null},'/my/');assert.equal(r.result.state,'auth');});

for(const [host,pathname] of [['lms-identity.nju.edu.cn','/login'],['lms.nju.edu.cn','/login']])test('identity entry remains interactive: '+host,()=>{const box={window:{__njuSource:'lms'},document:{querySelector:()=>null},location:{hostname:host,pathname}};assert.equal(vm.runInNewContext(source,box).state,'auth');});

test('ehall clicks the actual portal login before selecting the grades app',()=>{let loginClicks=0,gradeClicks=0;const controls=[{textContent:'登录',getClientRects:()=>[{}],click:()=>loginClicks++},{textContent:'我的成绩',getClientRects:()=>[{}],click:()=>gradeClicks++}];const r=run('ehall',{}, {querySelectorAll:()=>controls,querySelector:()=>null},'/ywtb-portal/official/index.html');assert.equal(loginClicks,1);assert.equal(gradeClicks,0);assert.equal(r.result.state,'navigating');});

function mobileBox({controls=[],identity,grade=false,path='/jwmobile/index',fetch}={}){
 const state={controls,grade};const box={window:{__njuSource:'ehall',...(identity?{__njuEhallIdentity:{status:identity}}:{})},location:{hostname:'ehallapp.nju.edu.cn',pathname:path,href:'https://ehallapp.nju.edu.cn'+path},URL,fetch,document:{querySelectorAll:()=>state.controls,querySelector:selector=>state.grade&&selector.includes('课程名')?{}:null}};return {box,state};
}
const control=(text,click)=>({textContent:text,getClientRects:()=>[{}],click});
test('mobile flow follows home then login before admitting the grade list',()=>{
 const actions=[];const {box,state}=mobileBox({identity:'anonymous'});
 const login=control('登录',()=>{actions.push('login');state.controls=[];state.grade=true;box.window.__njuEhallIdentity={status:'authenticated'};});
 state.controls=[control('主页',()=>{actions.push('home');state.controls=[login];})];
 assert.equal(vm.runInNewContext(source,box).state,'navigating');assert.deepEqual(actions,['home']);
 assert.equal(vm.runInNewContext(source,box).state,'navigating');assert.deepEqual(actions,['home','login']);
 assert.equal(vm.runInNewContext(source,box).state,'read');assert.deepEqual(actions,['home','login']);
});
test('portal goes home when login is not exposed, without attempting an application search',()=>{
 let home=0;const r=run('ehall',{}, {querySelectorAll:()=>[control('主页',()=>home++)],querySelector:()=>{throw new Error('must not search before going home');}},'/ywtb-portal/official/index.html');assert.equal(home,1);assert.equal(r.result.state,'navigating');
});
test('authenticated mobile grade page never bounces back to its visible home control',()=>{
 let home=0;const {box}=mobileBox({identity:'authenticated',grade:true,controls:[control('主页',()=>home++)]});assert.equal(vm.runInNewContext(source,box).state,'read');assert.equal(home,0);
});
test('mobile URL and uninitialized application shell alone are not ready',()=>{
 for(const path of ['/jwmobile','/jwmobile/index','/other-app']){const {box}=mobileBox({identity:'authenticated',path});assert.equal(vm.runInNewContext(source,box).state,'wait');}
});
test('mobile identity check waits for the actual authenticated response',async()=>{
 let finish,requests=0;const {box,state}=mobileBox({grade:true,fetch:()=>{requests++;return new Promise(resolve=>finish=resolve);}});
 assert.equal(vm.runInNewContext(source,box).state,'wait');assert.equal(vm.runInNewContext(source,box).state,'wait');assert.equal(requests,1);
 finish({ok:true,url:'https://ehallapp.nju.edu.cn/jwmobile/biz/user/info',json:async()=>({code:200,xh:'synthetic-id',xm:'Synthetic student'})});await new Promise(resolve=>setImmediate(resolve));assert.equal(vm.runInNewContext(source,box).state,'read');
});
test('anonymous identity response cannot admit the mobile grade shell',async()=>{
 const {box}=mobileBox({grade:true,fetch:async()=>({ok:true,url:'https://ehallapp.nju.edu.cn/jwmobile/biz/user/info',json:async()=>({code:401,data:null})})});assert.equal(vm.runInNewContext(source,box).state,'wait');await new Promise(resolve=>setImmediate(resolve));assert.equal(vm.runInNewContext(source,box).state,'auth');
});

test('portal clicks the real login button rather than its matching header-user wrapper',()=>{
 let wrapperClicks=0,buttonClicks=0;const button={...control('登录',()=>buttonClicks++),tagName:'BUTTON',children:[]};const wrapper={...control('登录',()=>wrapperClicks++),tagName:'DIV',children:[button]};
 const r=run('ehall',{}, {querySelectorAll:()=>[wrapper,button],querySelector:()=>null},'/ywtb-portal/official/index.html');assert.equal(wrapperClicks,0);assert.equal(buttonClicks,1);assert.equal(r.result.state,'navigating');
});
test('home navigation clicks the inner label so its parent handler can receive the bubbled event',()=>{
 let outerClicks=0,innerClicks=0;const label={...control('主页',()=>innerClicks++),tagName:'SPAN',children:[]};const wrapper={...control('主页',()=>outerClicks++),tagName:'DIV',children:[label]};
 const r=run('ehall',{}, {querySelectorAll:()=>[wrapper,label],querySelector:()=>null},'/ywtb-portal/official/index.html');assert.equal(outerClicks,0);assert.equal(innerClicks,1);assert.equal(r.result.state,'navigating');
});

test('HTTP and application success without the observed top-level student identity stays unauthenticated',async()=>{
 const {box}=mobileBox({grade:true,fetch:async()=>({ok:true,url:'https://ehallapp.nju.edu.cn/jwmobile/biz/user/info',json:async()=>({code:200,xh:''})})});assert.equal(vm.runInNewContext(source,box).state,'wait');await new Promise(resolve=>setImmediate(resolve));assert.equal(vm.runInNewContext(source,box).state,'auth');
});
