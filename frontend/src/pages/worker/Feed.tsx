import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { api } from '../../api/client';

export function Feed() {
  const { data: jobs } = useQuery({ queryKey: ['jobs'], queryFn: api.listJobs });
  return (
    <div className="space-y-3">
      <h1 className="text-xl font-semibold">Pick a job to label</h1>
      {jobs?.map(j => (
        <Link key={j.id} to={`/work/${j.id}`} className="block bg-white border rounded p-4 hover:shadow">
          <div className="font-medium">{j.name}</div>
          <div className="text-sm text-slate-600">{j.description}</div>
          <div className="text-xs text-slate-500 mt-1">{j.templateType}</div>
        </Link>
      ))}
    </div>
  );
}
