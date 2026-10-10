import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { percent } from './data';

export default function HistoryChart({ points }: { points: {horario: string; percentual: number}[] }) {
  return <div className="history-chart"><ResponsiveContainer width="100%" height="100%"><AreaChart data={points}>
    <defs><linearGradient id="history-fill" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stopColor="#bddf8f" stopOpacity={.28}/><stop offset="1" stopColor="#bddf8f" stopOpacity={0}/></linearGradient></defs>
    <CartesianGrid stroke="#26313e" vertical={false}/><XAxis dataKey="horario" tick={{fill:'#8391a3',fontSize:10}} axisLine={false} tickLine={false}/><YAxis domain={[0,100]} hide/>
    <Tooltip contentStyle={{background:'#17212d',border:'1px solid #344352',borderRadius:8}} formatter={v => [percent(Number(v)),'Seções']}/><Area dataKey="percentual" type="linear" stroke="#bddf8f" fill="url(#history-fill)" isAnimationActive={false}/>
  </AreaChart></ResponsiveContainer></div>;
}
