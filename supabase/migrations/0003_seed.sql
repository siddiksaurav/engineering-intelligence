insert into work_types (name, color, sort_order) values
 ('Feature','#4f46e5',1),('Bug Fix','#dc2626',2),('Code Review','#0891b2',3),
 ('Meeting','#f59e0b',4),('Ops/DevOps','#16a34a',5),('Support','#7c3aed',6),
 ('Docs','#64748b',7),('Research','#db2777',8);
-- starter technologies (devs can add more from the UI)
insert into technologies (name) values
 ('Spring Boot'),('Apache Kafka'),('PostgreSQL'),('Redis'),('React'),('Next.js'),
 ('TypeScript'),('Java'),('Docker'),('Kubernetes'),('AWS'),('GraphQL') on conflict do nothing;
-- Bootstrap: seed the first manager's email so they can log in and manage the rest.
insert into allowed_emails (email, role) values ('siddiquesaurav@gmail.com','manager') on conflict do nothing;
