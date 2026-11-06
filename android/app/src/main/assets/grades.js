/* Read-only adapters. Never log the response, page, cookies, or academic values. */
window.__njuGradeResult = null;
(async () => {
  const clean = v => String(v ?? '').replace(/\s+/g, ' ').trim();
  const failure = (message, kind = 'structure', extra = {}) => Object.assign(new Error(message), {kind, ...extra});
  const get = async (url, options = {}) => {
    const response = await fetch(url, {credentials:'same-origin', ...options});
    const final = new URL(response.url);
    if (response.status === 401 || final.origin !== location.origin || /\/(?:login|authserver)(?:\/|\?|$)/i.test(final.pathname)) throw failure('学校返回了登录页面，请重新进入平台认证','auth');
    if (!response.ok) throw failure('学校页面暂时无法读取（HTTP '+response.status+'）','network');
    return response;
  };
  const doc = async url => new DOMParser().parseFromString(await (await get(url)).text(), 'text/html');
  if (location.hostname === 'ehallapp.nju.edu.cn') {
    if(location.pathname.startsWith('/jwmobile/')){
      // Verified against the mobile page's own v410 client and live response schema.
      // Its session cannot use the desktop /jwapp endpoint (HTTP 403).
      const list=await (await get('/jwmobile/biz/v410/score/njuTermList')).json();
      if(Number(list?.code)!==200||!Array.isArray(list.data))throw failure('移动成绩学期列表未完整返回');
      const terms=list.data;
      const all=terms.find(t=>typeof t.termName==='string'&&/全部|所有|all/i.test(t.termName));
      const requested=all?[all]:terms;
      if(requested.length>100)throw failure('学期数量超出保护范围，原成绩缓存保留');
      const records=[],seen=new Set();
      for(const term of requested){
        if(term.termCode==null||term.termCode==='')throw failure('移动成绩学期缺少查询代码');
        const response=await (await get('/jwmobile/biz/v410/score/termScore',{
          method:'POST',headers:{'Content-Type':'application/json'},
          body:JSON.stringify({termCode:term.termCode,courseNature:'',courseName:''})
        })).json();
        if(Number(response?.code)!==200||!Array.isArray(response.data?.termScoreList))throw failure('移动课程成绩返回不完整，原缓存保留');
        if(response.data.tipCode!=null&&response.data.tipCode!=='')throw failure('学校暂未开放完整成绩，原缓存保留');
        for(const group of response.data.termScoreList){
          if(!Array.isArray(group.scoreList)||group.termCode==null)throw failure('移动成绩分组格式发生变化');
          for(let index=0;index<group.scoreList.length;index++){
            const row=group.scoreList[index];if(!row||!row.courseName)throw failure('移动成绩缺少课程名称');
            const termCode=String(row.termCode??group.termCode),termName=clean(row.termName||group.termName||termCode);
            const id=String(row.id??[termCode,row.courseId||row.courseName,row.examProp||'',index].join(':'));
            if(seen.has(id))continue;seen.add(id);
            const score=row.score!=null&&row.score!==''?row.score:row.rankScoreName;
            const detail=[];
            if(row.regularScore!=null)detail.push('平时成绩：'+String(row.regularScore));
            if(row.finalExamScore!=null)detail.push('期末成绩：'+String(row.finalExamScore));
            records.push({id,course:clean(row.courseName),term:termName,termKey:termCode,score:clean(score)||'暂无成绩',detail:detail.join('\n')});
          }
        }
      }
      return {source:'ehall',records,coverage:'all-terms',reader:'mobile-v410'};
    }
    if (!location.pathname.startsWith('/jwapp/sys/cjcx/') && !location.pathname.startsWith('/jwmobile/')) throw new Error('请通过办事大厅正式入口进入我的成绩');
    const rows = [], seen = new Set();
    for (let page=0;page<100;page++) {
      const body = new URLSearchParams({querySetting:JSON.stringify([{name:'SFYX',value:'1',linkOpt:'AND',builder:'m_value_equal'}]), '*order':'-XNXQDM,-KCH,-KXH',pageSize:'500',pageNumber:String(page)});
      const json = await (await get('/jwapp/sys/cjcx/modules/cjcx/xscjcx.do', {method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:body.toString()})).json();
      const data = json?.datas?.xscjcx;
      if (!data || !Array.isArray(data.rows)) throw new Error('成绩格式异常');
      if (data.totalSize == null || data.totalSize === '' || !Number.isInteger(Number(data.totalSize)) || Number(data.totalSize)<0) throw new Error('成绩缺少可验证的总数，保留旧缓存');
      let added=0;
      for (const row of data.rows) {
        const id=String(row.WID || [row.XNXQDM,row.KCH,row.KXH].join(':'));
        if (seen.has(id)) continue;
        if (!row.XNXQDM || !(row.XSKCM || row.KCH)) throw new Error('成绩缺少课程信息');
        seen.add(id);added++;
        const score = row.ZCJ != null && row.ZCJ !== '' ? row.ZCJ : row.DJCJMC;
        rows.push({id,course:clean(row.XSKCM || row.KCH),term:clean(row.XNXQDM_DISPLAY || row.XNXQDM),termKey:clean(row.XNXQDM),score:clean(score) || '暂无成绩',detail:''});
      }
      const total=Number(data.totalSize);
      if (rows.length===total) return {source:'ehall',records:rows};
      if (rows.length>total || data.rows.length<500) throw new Error('成绩条目与分页总数不一致，保留旧缓存');
      if (!added) throw new Error('成绩分页重复，已保留旧缓存');
    }
    throw new Error('成绩分页超出保护上限');
  }
  if (location.hostname === 'elite.nju.edu.cn') {
    const anonymous=window.loginUtils?.isAnonymous?.() ?? window.__env?.anonymous;
    if (anonymous===true || document.querySelector('input[type="password"],form[action*="/login"]')) throw failure('尚未建立交换生系统身份，请完成学生统一认证','auth');
    if(anonymous!==false&&!document.querySelector('.login-in'))throw failure('交换生系统身份状态尚未就绪','loading');
    const page=location.pathname === '/exchangesystem/index/create' && new URL(location.href).searchParams.get('pid')==='4' ? document : await doc('/exchangesystem/index/create?pid=4');
    const field = name => {
      const bracket = name.replace(/^data\.(.+)$/, 'data[$1]');
      const el = page.getElementsByName(name)[0] || page.getElementsByName(bracket)[0] || page.getElementById(name) || page.getElementById(name.replace('.', '_'));
      return el ? clean(el.value || el.textContent) : '';
    };
    const total=field('data.zyzrs'),percent=field('data.pmbfb');
    // GPA field names vary; bind only to a displayed label rather than inventing an API.
    let gpa='';
    const readValue = element => {
      if (!element) return '';
      const input = element.matches?.('input,output,select') ? element : element.querySelector?.('input:not([type="hidden"]),output,select');
      return clean(input ? input.value || input.textContent : element.textContent);
    };
    for (const label of page.querySelectorAll('th, label, .control-label, td, div, span')) {
      if (['DIV','SPAN'].includes(label.tagName) && label.children?.length) continue;
      const labelText=clean(label.textContent);
      if (!/平均学分[绩积]/.test(labelText)) continue;
      const related=label.htmlFor && page.getElementById(label.htmlFor);
      if (related) gpa=readValue(related);
      if (!gpa) gpa=readValue(label.nextElementSibling);
      if (!gpa && label.parentElement?.matches('.control-label,th,td')) gpa=readValue(label.parentElement.nextElementSibling);
      if (!gpa) gpa=labelText.match(/平均学分[绩积](?:点)?\s*[:：]?\s*(\d+(?:\.\d+)?)/)?.[1] || '';
      if (gpa && !/^\d+(?:\.\d+)?$/.test(gpa)) gpa='';
      if(gpa)break;
    }
    if (!gpa || !total || !percent) throw failure('申请页面已打开，但总览字段尚未完整识别；登录状态未判定为失效','structure',{code:'elite_fields_missing',missingFields:[!gpa?'gpa':null,!total?'total':null,!percent?'percent':null].filter(Boolean)});
    return {source:'elite',records:[],summary:{gpa,percent,total}};
  }
  if (location.hostname === 'ggtypt.nju.edu.cn') {
    // Establish the separate PTM session using the reference plugin's navigation path.
    if (!location.pathname.startsWith('/ptm/')) throw new Error('请先点击体育教学会话入口，完成跳转后再读取');
    const initial=await doc('/ptm/student/score/course?termId=123');
    const terms=[...initial.querySelectorAll('#termId option')].map(o=>({id:o.value,name:clean(o.textContent)})).filter(t=>t.id&&t.name);
    if (!terms.length) throw new Error('未取得学期列表，请重新建立体育教学会话');
    const records=[];
    for (const term of terms) {
      const page=await doc('/ptm/student/score/course?termId='+encodeURIComponent(term.id));
      if (!page.querySelector('#termId')) throw new Error('体育学期页面不完整');
      for (const row of page.querySelectorAll('tbody tr')) {
        const cells=row.querySelectorAll('td'),link=row.querySelector('a[href*="courseId="]');
        if (!link) continue;
        if (cells.length<4) throw new Error('体育课程结构异常');
        const id=new URL(link.getAttribute('href'),location.href).searchParams.get('courseId');
        if (!id) throw new Error('体育课程缺少标识');
        const detail=await doc('/ptm/student/score/detail?courseId='+encodeURIComponent(id)+'&');
        const values=[...detail.querySelectorAll('.profile-info-row')].map(r=>({name:clean(r.querySelector('.profile-info-name')?.textContent),value:clean(r.querySelector('.profile-info-value')?.textContent)})).filter(v=>v.name);
        if (!values.length) throw new Error('体育详细成绩缺失');
        const overall=values.find(v=>/^(总评|总评成绩|总成绩|最终成绩)$/.test(v.name));
        records.push({id,course:clean(cells[1].textContent).split('|')[0].trim(),term:term.name,score:overall?.value || '详见明细',detail:values.map(v=>v.name+'：'+v.value).join('\n')});
      }
    }
    return {source:'pe',records};
  }
  throw new Error('请进入对应的成绩平台');
})().then(result=>{window.__njuGradeResult=result;}).catch(error=>{window.__njuGradeResult={error:error.kind?error.message:'页面数据结构未识别，旧缓存保留',errorKind:error.kind || 'structure',errorCode:error.code || '',missingFields:error.missingFields || []};});
