export async function caseApi(request,path='',method='GET',data={}) {
  const result=await request('/lawyer/case'+path,method,data,{'content-type':'application/json'});
  if(!result||Number(result.code)!==200)throw new Error(result&&result.msg||'案件操作失败，请重试');
  return result;
}
