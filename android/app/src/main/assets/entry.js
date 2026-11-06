/* Drive only observed school login/portal controls; no account values are returned. */
(() => {
 const source=window.__njuSource, host=location.hostname;
 const visible=e=>e&&e.getClientRects().length>0;
 if(source==='elite'&&host==='elite.nju.edu.cn'){
  const anonymous=window.loginUtils?.isAnonymous?.() ?? window.__env?.anonymous;
  if(anonymous===true){
   if(location.pathname.includes('/index/create')){location.replace('/exchangesystem/');return {state:'navigating'};}
   const student=document.querySelector('#loginCAS');
   if(student){if(!window.__njuStudentLoginStarted){window.__njuStudentLoginStarted=true;student.click();}return {state:'navigating'};}
   return {state:'wait'};
  }
  if(anonymous===false||document.querySelector('.login-in'))return {state:'read'};
  return {state:'wait'};
 }
 if(source==='ehall'&&['ehall.nju.edu.cn','ehallapp.nju.edu.cn'].includes(host)){
  const controls=[...document.querySelectorAll('a,button,span,div,[role="button"]')];
  const named=name=>{
   const matches=controls.filter(e=>visible(e)&&e.textContent.trim()===name);
   return matches.find(e=>e.tagName==='BUTTON')||matches.find(e=>e.tagName==='A')||matches.find(e=>e.getAttribute?.('role')==='button')||matches.find(e=>![...(e.children||[])].some(child=>child.textContent.trim()===name));
  };
  const clickOnce=(element,key)=>{if(!window[key]){window[key]=true;delete window.__njuEhallIdentity;element.click();}return {state:'navigating'};};
  const login=named('登录');
  if(login)return clickOnce(login,'__njuPortalLoginStarted');
  const home=named('主页'),entry=named('我的成绩')||named('成绩查询');
  if(host==='ehallapp.nju.edu.cn'){
   if(document.querySelector('input[type="password"],input[name*=captcha],input[id*=captcha]'))return {state:'auth'};
   const mobile=/^\/jwmobile(?:\/|$)/.test(location.pathname);
   if(mobile){
    // A mobile URL alone is not proof of login. This is the page's own identity request.
    if(!window.__njuEhallIdentity){
     const state=window.__njuEhallIdentity={status:'pending'};
     fetch('/jwmobile/biz/user/info',{credentials:'same-origin'}).then(response=>{
      const target=new URL(response.url||location.href,location.href);
      if(!response.ok||target.hostname!==host||/\/(?:login|authserver)(?:\/|$)/i.test(target.pathname))throw new Error('identity not established');
      return response.json();
     }).then(result=>{state.status=Number(result?.code)===200&&typeof result.xh==='string'&&result.xh.trim().length>0?'authenticated':'anonymous';}).catch(()=>{state.status='anonymous';});
    }
    const identity=window.__njuEhallIdentity.status;
    if(identity==='pending')return {state:'wait'};
    const gradeView=document.querySelector('input[placeholder*="课程名"],input[placeholder*="课程号"],[class*="rowWrapper"]');
    if(identity==='authenticated'&&gradeView)return {state:'read'};
    if(identity==='authenticated'&&entry)return clickOnce(entry,'__njuGradeEntryClicked');
    if(home)return clickOnce(home,'__njuEhallHomeStarted');
    return {state:identity==='anonymous'?'auth':'wait'};
   }
   if(home)return clickOnce(home,'__njuEhallHomeStarted');
   if(/^\/jwapp\/sys\/cjcx(?:\/|$)/.test(location.pathname))return {state:'read'};
   return {state:'wait'};
  }
  if(entry){if(!window.__njuGradeEntryClicked){window.__njuGradeEntryClicked=true;entry.click();}return {state:'wait'};}
  if(home)return clickOnce(home,'__njuEhallHomeStarted');
  if(!window.__njuGradeSearchStarted){
   const search=[...document.querySelectorAll('button')].find(e=>visible(e)&&e.textContent.trim()==='搜索');
   const input=document.querySelector('input[placeholder*="搜索"],input[placeholder*="应用"],input[type="search"]') || search?.parentElement?.querySelector('input');
   if(input&&search){window.__njuGradeSearchStarted=true;input.focus();input.value='我的成绩';input.dispatchEvent(new Event('input',{bubbles:true}));input.dispatchEvent(new Event('change',{bubbles:true}));search.click();}
  }
  return {state:'wait'};
 }
 if(source==='moodle'&&host==='selearning.nju.edu.cn'&&location.pathname.includes('/login/')){
  const sso=[...document.querySelectorAll('a,button')].find(e=>/统一身份认证/.test(e.textContent));
  if(sso&&!window.__njuMoodleSSOStarted){window.__njuMoodleSSOStarted=true;sso.click();return {state:'navigating'};}
  return {state:'auth'};
 }
 if(host==='authserver.nju.edu.cn'||host==='lms-identity.nju.edu.cn'||host==='lms.nju.edu.cn'&&/\/login(?:\/|$)/.test(location.pathname)||document.querySelector('input[type="password"],input[name*=captcha],input[id*=captcha]'))return {state:'auth'};
 if(source==='moodle'&&host==='selearning.nju.edu.cn'&&!document.querySelector('.usermenu, [data-region="usermenu"], .userpicture'))return {state:'wait'};
 if(source==='lms'&&host==='lms.nju.edu.cn'&&!/\/login(?:\/|$)/.test(location.pathname)&&!window.globalData?.user?.id)return {state:'wait'};
 return {state:'ready'};
})()
