const {test}=require('node:test');
const assert=require('node:assert/strict');
const vm=require('node:vm');
const fs=require('node:fs');
const path=require('node:path');
const script=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/grades.js'),'utf8');
async function run(data,pathname='/jwapp/sys/cjcx/*default/index.do'){
 const sandbox={window:{},location:{hostname:'ehallapp.nju.edu.cn',origin:'https://ehallapp.nju.edu.cn',pathname},URL,URLSearchParams,
 fetch:async()=>({ok:true,url:'https://ehallapp.nju.edu.cn/jwapp/sys/cjcx/modules/cjcx/xscjcx.do',json:async()=>({datas:{xscjcx:data}})})};
 vm.runInNewContext(script,sandbox);for(let i=0;i<20;i++){await new Promise(r=>setImmediate(r));if(sandbox.window.__njuGradeResult)return JSON.parse(JSON.stringify(sandbox.window.__njuGradeResult));}throw new Error('did not settle');
}
test('ehall keeps a grade-only course as its original label',async()=>{const r=await run({totalSize:1,rows:[{WID:'synthetic',XNXQDM:'synthetic-term',XSKCM:'Synthetic course',ZCJ:'',DJCJMC:'Synthetic grade label'}]});assert.equal(r.records[0].score,'Synthetic grade label');});
test('ehall preserves numeric zero rather than replacing it with a label',async()=>{const r=await run({totalSize:1,rows:[{WID:'synthetic',XNXQDM:'synthetic-term',XSKCM:'Synthetic course',ZCJ:0,DJCJMC:'Synthetic label'}]});assert.equal(r.records[0].score,'0');});
test('ehall accepts an explicitly confirmed empty list',async()=>{const r=await run({totalSize:0,rows:[]});assert.deepEqual(r.records,[]);});
test('ehall rejects an unconfirmed empty response',async()=>{assert.ok((await run({rows:[]})).error);});
test('ehall rejects a truncated response',async()=>{assert.ok((await run({totalSize:2,rows:[{WID:'synthetic',XNXQDM:'synthetic-term',XSKCM:'Synthetic course',ZCJ:'Synthetic grade'}]})).error);});
async function mobile(fetch){const sandbox={window:{},location:{hostname:'ehallapp.nju.edu.cn',origin:'https://ehallapp.nju.edu.cn',pathname:'/jwmobile/index'},URL,URLSearchParams,fetch};vm.runInNewContext(script,sandbox);for(let i=0;i<50;i++){await new Promise(r=>setImmediate(r));if(sandbox.window.__njuGradeResult)return JSON.parse(JSON.stringify(sandbox.window.__njuGradeResult));}throw new Error('mobile did not settle');}
const mobileResponse=(url,data,ok=true)=>({ok,status:ok?200:503,url:'https://ehallapp.nju.edu.cn'+url,json:async()=>data});
test('ehall mobile uses observed all-term POST and never calls desktop endpoint',async()=>{const calls=[];const result=await mobile(async(url,options)=>{calls.push({url,options});return mobileResponse(url,url.endsWith('njuTermList')?{code:200,data:[{termCode:'synthetic-all',termName:'全部学期'}]}:{code:200,data:{tipCode:null,termScoreList:[{termCode:'synthetic-term',termName:'Synthetic term',scoreList:[{id:null,courseId:'synthetic',courseName:'Synthetic course',score:'Synthetic grade'}]}]}});});assert.equal(result.records.length,1);assert.equal(result.coverage,'all-terms');assert.equal(calls[1].options.method,'POST');assert.equal(JSON.parse(calls[1].options.body).termCode,'synthetic-all');assert.ok(calls.every(c=>c.url.startsWith('/jwmobile/')));});
test('ehall mobile preserves a grade label and traverses terms when no all-term option',async()=>{let posts=0;const result=await mobile(async(url,options)=>{if(url.endsWith('njuTermList'))return mobileResponse(url,{code:200,data:[{termCode:'a',termName:'A'},{termCode:'b',termName:'B'}]});posts++;const term=JSON.parse(options.body).termCode;return mobileResponse(url,{code:200,data:{termScoreList:[{termCode:term,scoreList:[{id:null,courseName:'Synthetic '+term,score:'',rankScoreName:'Synthetic label'}]}]}});});assert.equal(posts,2);assert.equal(result.records.length,2);assert.equal(result.records[0].score,'Synthetic label');});
test('ehall mobile a failed term cannot replace the complete cache',async()=>{const result=await mobile(async(url)=>mobileResponse(url,url.endsWith('njuTermList')?{code:200,data:[{termCode:'a',termName:'A'}]}:{code:200,data:{} }));assert.ok(result.error);assert.equal(result.records,undefined);});

async function elite(document){
 const sandbox={window:{__env:{anonymous:document.__anonymous ?? false}},document,location:{hostname:'elite.nju.edu.cn',origin:'http://elite.nju.edu.cn',pathname:'/exchangesystem/index/create',href:'http://elite.nju.edu.cn/exchangesystem/index/create?pid=4'},URL,URLSearchParams};
 vm.runInNewContext(script,sandbox);for(let i=0;i<20;i++){await new Promise(r=>setImmediate(r));if(sandbox.window.__njuGradeResult)return JSON.parse(JSON.stringify(sandbox.window.__njuGradeResult));}throw new Error('elite did not settle');
}
test('elite missing summary fields is a structure error, never an expired login',async()=>{
 const result=await elite({querySelector:()=>null,getElementsByName:name=>['data.zyzrs','data.pmbfb'].includes(name)?[{value:'0'}]:[],getElementById:()=>null,querySelectorAll:()=>[]});
 assert.equal(result.errorKind,'structure');assert.equal(result.errorCode,'elite_fields_missing');assert.deepEqual(result.missingFields,['gpa']);
});
test('elite binds a GPA label in any table cell, including a second column',async()=>{
 const cell={textContent:'平均学分绩',nextElementSibling:{matches:()=>false,querySelector:()=>({value:'0'})}};
 const result=await elite({querySelector:()=>null,getElementsByName:()=>[{value:'0'}],getElementById:()=>null,querySelectorAll:()=>[{textContent:'其他标签'},cell]});
 assert.ok(result.summary);assert.equal(result.source,'elite');
});
test('elite visible password form is an explicit authentication error',async()=>{
 const result=await elite({querySelector:()=>({type:'password'})});assert.equal(result.errorKind,'auth');
});
test('elite actual LayUI div label and adjacent div value are supported',async()=>{
 const label={tagName:'DIV',children:[],textContent:'平均学分绩',nextElementSibling:{matches:()=>false,querySelector:()=>({value:'0'})}};
 const result=await elite({querySelector:()=>null,getElementsByName:()=>[{value:'0'}],getElementById:()=>null,querySelectorAll:()=>[label]});assert.ok(result.summary);assert.equal(result.source,'elite');
});
test('elite blank anonymous application is never accepted as an authenticated page',async()=>{const result=await elite({__anonymous:true,querySelector:()=>null});assert.equal(result.errorKind,'auth');});
