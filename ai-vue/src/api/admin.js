import service from '@/utils/request'

export function login(data){
    return service.post('/user/login',data)
}

export function register(data){
    return service.post('/user/register',data)
}

// 发送注册验证码。
// 单独放宽超时：request.js 里的默认 timeout 是 5000ms，而 SMTP 发信经常超过 5 秒，
// 用默认值会在邮件其实已经发出去的情况下报「请求超时」，用户会反复重试。
export function sendCode(data){
    return service.post('/user/email-code',data,{timeout:15000})
}


export  function categoryTree(){
    return service.get('/knowledge/category/tree')
}

export function articlePage(param){
    return service.get('/knowledge/category/tree',{param})
}
export function  getConsultationPage(params){
    return service.get('/psychological-chat/sessions',{params})
}