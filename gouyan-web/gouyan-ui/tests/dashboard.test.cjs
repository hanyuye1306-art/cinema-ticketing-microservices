const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),compiler=require('vue-template-compiler'),babel=require('@babel/core');
function loadView(name,api={}) {
 const root=path.resolve(__dirname,'../src');const session={sysUser:{userId:42,userName:'test-admin',userPicture:'["/images/user/actual.jpg"]',sysRole:{children:[{children:[{path:'movie'}]}]}}};
 const storage={getItem:()=>JSON.stringify(session)}; const globals={axios:api,sessionStorage:storage,window:{innerWidth:1440,sessionStorage:storage}};
 function load(file){const module={exports:{}};const source=fs.readFileSync(file,'utf8');const script=file.endsWith('.vue') ? compiler.parseComponent(source).script.content : source;const {code}=babel.transformSync(script,{babelrc:false,configFile:false,plugins:[require.resolve('@babel/plugin-transform-modules-commonjs')]});vm.runInNewContext(code,{...globals,module,exports:module.exports,require:name=>name.startsWith('@/')?load(path.join(root,name.slice(2))+'.js'):require(name)});return module.exports;}
 const component=load(path.join(root,'views',name+'.vue')).default,view={global:{base:'http://localhost:8181'},$root:{$on(){},$off(){}}};Object.assign(view,component.data.call(view));for(const [key,value] of Object.entries(component.methods))view[key]=value.bind(view);for(const [key,get] of Object.entries(component.computed))Object.defineProperty(view,key,{get:()=>get.call(view)});return view;
}
test('dashboard displays API totals, fills missing dates with zero and honors menu permissions',async()=>{
 const view=loadView('Welcome',{get:async()=>({data:{code:200,data:{updatedAt:'2026-11-18 10:00:00',counts:{movies:28,todayRevenue:12.5},weeklyOrders:[{day:'2026-11-16',count:3}]}}})});await view.load();assert.equal(view.value('movies'), '28');assert.equal(view.value('todayRevenue',true),'12.50');assert.equal(view.weekly.length,7);assert.equal(view.weeklyTotal,3);assert.equal(view.weekly[6].count,0);assert.equal(view.shortcuts.length,1);assert.equal(view.shortcuts[0].path,'/movie');
});
test('dashboard API failure clears stale figures and remains retryable',async()=>{
 let fail=false;const view=loadView('Welcome',{get:async()=>({data:fail?{code:500,msg:'unavailable'}:{code:200,data:{counts:{movies:28}}}})});await view.load();assert.equal(view.ready,true);fail=true;await view.load();assert.equal(view.ready,false);assert.equal(view.loading,false);assert.equal(view.value('movies'),'—');assert.ok(view.error.includes('unavailable'));fail=false;await view.load();assert.equal(view.ready,true);
});
test('header reads the actual database avatar and resets image fallback after profile updates',async()=>{
 const view=loadView('Home',{get:async()=>({data:{code:200,data:{userId:42,userName:'test-admin',userPicture:'["/images/user/database.jpg"]'}}})});assert.equal(view.avatarUrl,'http://localhost:8181/images/user/actual.jpg');await view.refreshProfile();assert.equal(view.avatarUrl,'http://localhost:8181/images/user/database.jpg');view.avatarFailed=true;view.updateProfile({userPicture:'[]'});assert.equal(view.avatarUrl,'');assert.equal(view.avatarFailed,false);
});
