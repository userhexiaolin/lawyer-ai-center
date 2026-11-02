export const primaryNavigation = [
  { name: 'chat', label: '新对话' },
  { name: 'contractReview', label: '合同审查' },
  { name: 'templateManagement', label: '模板管理' },
  { name: 'drafting', label: '文书起草' },
  { name: 'myCases', label: '我的案件' },
  { name: 'factSearch', label: '案情检索' },
  { name: 'notifications', label: '通知' },
] as const;
export const mobileCoreNavigation = primaryNavigation.filter(item => item.name === 'templateManagement' || item.name === 'drafting');
export const moreNavigation = [
  { name: 'appMarket', label: '应用市场' },
  { name: 'mediaWorkbench', label: '媒体工作台' },
  { name: 'help', label: '帮助' },
] as const;
export function navigationActive(name: string, current: string) {
  return (
    name === current
    || (name === 'myCases' && current === 'caseMaterialsWorkbench')
    || (name === 'chat' && current === 'chatWithId')
    || (name === 'drafting' && current === 'legalOpinion')
    || (name === 'templateManagement' && current === 'templateCategories')
    || (name === 'factSearch' && ['searchReports', 'searchReportWorkbench'].includes(current))
  );
}
